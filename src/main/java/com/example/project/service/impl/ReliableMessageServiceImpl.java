package com.example.project.service.impl;

import com.example.project.entity.MessageTable;
import com.example.project.mapper.MessageTableMapper;
import com.example.project.mq.MqConstants;
import com.example.project.mq.dto.OrderCancelledMessage;
import com.example.project.mq.dto.OrderPaidMessage;
import com.example.project.mq.producer.OrderKafkaProducer;
import com.example.project.service.ReliableMessageService;
import com.example.project.util.DistributedLockUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 可靠消息服务实现 —— 修复批次 0 评审指出的两个缺陷
 * <p>
 * 缺陷①修复：doSend 与补偿任务统一收敛到 {@link #sendAndMarkSent} —— 发送成功必须 updateToSent，
 * 否则补偿任务会重发已成功的消息。
 * 缺陷②修复：补偿任务 {@link #compensatePendingMessages} 加 Redisson 分布式锁 "msg:compensate"，
 * 防多实例重复扫描。
 * <p>
 * 事务边界：savePendingMessage 必须在事务内调用；afterCommit 回调里不能抛异常（事务已提交），
 * 失败留给补偿任务兜底（30s 轮询 + 分布式锁 + 指数退避）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReliableMessageServiceImpl implements ReliableMessageService {

    private static final String COMPENSATE_LOCK_KEY = "msg:compensate";
    private static final int MAX_BACKOFF_SECONDS = 3600;

    /** topic → 消息 DTO 类型（补偿反序列化用） */
    private static final Map<String, Class<?>> TOPIC_DTO_CLASS = Map.of(
            MqConstants.TOPIC_ORDER_PAID, OrderPaidMessage.class,
            MqConstants.TOPIC_ORDER_CANCELLED, OrderCancelledMessage.class);

    private final MessageTableMapper messageTableMapper;
    private final OrderKafkaProducer orderKafkaProducer;
    private final DistributedLockUtil distributedLockUtil;

    /** Jackson 3 JsonMapper（与 SecurityConfig 风格一致），Lombok 不将其纳入构造器 */
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    /** 补偿任务分布式锁持有时间（秒），需 ≥ 扫描间隔/1000 */
    @Value("${reliable-message.compensate.lock-lease-seconds:30}")
    private long lockLeaseSeconds;

    /** 消息最大重试次数（写入 message_table.max_retries 与补偿超限判断） */
    @Value("${reliable-message.compensate.max-retries:3}")
    private int maxRetries;

    /** 指数退避基数（秒）：30s × 2^n，上限 1h */
    @Value("${reliable-message.compensate.backoff-base-seconds:30}")
    private int backoffBaseSeconds;

    @Override
    public Long savePendingMessage(String topic, String messageKey, Object dto) {
        MessageTable message = new MessageTable();
        message.setTopic(topic);
        message.setMessageKey(messageKey);
        message.setPayload(writePayload(dto));
        message.setMaxRetries(maxRetries);
        messageTableMapper.insert(message);
        log.info("[可靠消息] 写 PENDING 消息：msgId={}, topic={}, key={}", message.getId(), topic, messageKey);
        return message.getId();
    }

    @Override
    public void sendAfterCommit(Long msgId, String topic, String messageKey, Object dto) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sendAndMarkSent(msgId, topic, messageKey, dto, 0, maxRetries);
                }
            });
        } else {
            // 无事务（测试直调）兜底：直接发送
            sendAndMarkSent(msgId, topic, messageKey, dto, 0, maxRetries);
        }
    }

    /**
     * ★ 缺陷①修复：主流程与补偿任务统一走这里 —— 发送成功必须 updateToSent。
     * 发送失败不抛异常（afterCommit 阶段抛异常无意义），PENDING 由补偿任务兜底，
     * 并按指数退避累计重试次数，超限标记 FAILED。
     */
    private void sendAndMarkSent(Long msgId, String topic, String key, Object dto,
                                 int retryCount, int maxRetries) {
        orderKafkaProducer.send(topic, key, dto).whenComplete((result, ex) -> {
            if (ex == null) {
                messageTableMapper.updateToSent(msgId);
                log.info("[可靠消息] 发送成功并标记 SENT：msgId={}, topic={}", msgId, topic);
            } else {
                log.error("[可靠消息] 发送失败，等待补偿：msgId={}, topic={}, retryCount={}", msgId, topic, retryCount, ex);
                int nextRetry = retryCount + 1;
                if (nextRetry >= maxRetries) {
                    messageTableMapper.updateToFailed(msgId);
                    log.warn("[可靠消息] 超过最大重试次数，标记 FAILED：msgId={}, topic={}", msgId, topic);
                } else {
                    int delaySeconds = Math.min(backoffBaseSeconds * (1 << retryCount), MAX_BACKOFF_SECONDS);
                    messageTableMapper.incrementRetryCount(msgId, delaySeconds);
                }
            }
        });
    }

    /**
     * ★ 缺陷②修复：补偿任务加分布式锁，防多实例重复扫描。
     * 扫描条件由 Mapper 保证：status=PENDING 且 (next_retry_at IS NULL 或已到期) 且 retry_count &lt; max_retries。
     */
    @Override
    @Scheduled(fixedDelayString = "${reliable-message.compensate.fixed-delay-ms:30000}")
    public void compensatePendingMessages() {
        if (!distributedLockUtil.tryLock(COMPENSATE_LOCK_KEY, 0, lockLeaseSeconds, TimeUnit.SECONDS)) {
            log.debug("补偿任务已被其他实例执行，跳过本次扫描");
            return;
        }
        try {
            List<MessageTable> pending = messageTableMapper.findPendingForRetry();
            log.info("[可靠消息] 补偿扫描：待发送消息 {} 条", pending.size());
            for (MessageTable message : pending) {
                Class<?> clazz = TOPIC_DTO_CLASS.get(message.getTopic());
                if (clazz == null) {
                    log.error("[可靠消息] 未知 topic，标记 FAILED：msgId={}, topic={}", message.getId(), message.getTopic());
                    messageTableMapper.updateToFailed(message.getId());
                    continue;
                }
                Object dto;
                try {
                    dto = jsonMapper.readValue(message.getPayload(), clazz);
                } catch (Exception e) {
                    log.error("[可靠消息] 消息反序列化失败，标记 FAILED：msgId={}, topic={}", message.getId(), message.getTopic(), e);
                    messageTableMapper.updateToFailed(message.getId());
                    continue;
                }
                sendAndMarkSent(message.getId(), message.getTopic(), message.getMessageKey(), dto,
                        message.getRetryCount(), message.getMaxRetries());
            }
        } finally {
            distributedLockUtil.unlock(COMPENSATE_LOCK_KEY);
        }
    }

    private String writePayload(Object dto) {
        try {
            return jsonMapper.writeValueAsString(dto);
        } catch (Exception e) {
            throw new IllegalStateException("[可靠消息] 消息序列化失败: " + e.getMessage(), e);
        }
    }
}
