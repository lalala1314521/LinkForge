package com.example.project.mq.producer;

import com.example.project.mq.MqConstants;
import com.example.project.mq.dto.OrderCancelledMessage;
import com.example.project.mq.dto.OrderPaidMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

/**
 * 订单事件 Kafka 生产者
 * <p>
 * 通用 {@link #send} 供 ReliableMessageService 使用（本地消息表驱动，发送成功后由消息表标记 SENT）；
 * 原 whenComplete 里误导性的 throw（P0-5 死代码：异步回调抛异常无法回滚事务）已移除，
 * 改为日志记录，可靠性交给本地消息表补偿任务兜底。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderKafkaProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * 通用发送（ReliableMessageService 用）
     * Key 使用 userId 可保证同一用户的消息有序（分区有序性）
     */
    public CompletableFuture<SendResult<String, Object>> send(String topic, String key, Object message) {
        return kafkaTemplate.send(topic, key, message);
    }

    /**
     * 发送订单支付成功消息（保留兼容旧调用，内部委托通用 send）
     */
    public void sendOrderPaid(OrderPaidMessage message) {
        send(MqConstants.TOPIC_ORDER_PAID, String.valueOf(message.getUserId()), message)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("订单支付消息发送成功: topic={}, partition={}, offset={}, orderNo={}",
                                result.getRecordMetadata().topic(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset(),
                                message.getOrderNo());
                    } else {
                        // 不再 throw：异步回调抛异常无法回滚已提交事务，可靠性由本地消息表补偿任务兜底
                        log.error("订单支付消息发送失败: orderNo={}", message.getOrderNo(), ex);
                    }
                });
    }

    /**
     * 发送订单取消消息（保留兼容旧调用，内部委托通用 send）
     */
    public void sendOrderCancelled(OrderCancelledMessage message) {
        send(MqConstants.TOPIC_ORDER_CANCELLED, String.valueOf(message.getUserId()), message)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("订单取消消息发送成功: topic={}, partition={}, offset={}, orderNo={}",
                                result.getRecordMetadata().topic(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset(),
                                message.getOrderNo());
                    } else {
                        log.error("订单取消消息发送失败: orderNo={}", message.getOrderNo(), ex);
                    }
                });
    }
}
