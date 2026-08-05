package com.example.project.service;

import com.example.project.entity.MessageTable;
import com.example.project.mapper.MessageTableMapper;
import com.example.project.mq.MqConstants;
import com.example.project.mq.dto.OrderPaidMessage;
import com.example.project.mq.producer.OrderKafkaProducer;
import com.example.project.service.impl.ReliableMessageServiceImpl;
import com.example.project.util.DistributedLockUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 可靠消息服务测试（纯 Mockito 单测）
 * <p>
 * 覆盖：savePendingMessage 返回 id、doSend→SENT（缺陷①修复）、发送失败→重试计数、
 * 补偿任务（锁未获取跳过 / 正常扫描发送 / 未知 topic 标记 FAILED）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("可靠消息服务测试")
class ReliableMessageServiceTest {

    @Mock MessageTableMapper messageTableMapper;
    @Mock OrderKafkaProducer orderKafkaProducer;
    @Mock DistributedLockUtil distributedLockUtil;

    @InjectMocks ReliableMessageServiceImpl reliableMessageService;

    @BeforeEach
    void setUp() {
        // @Value 字段在纯单测下默认 0，用反射注入测试值
        ReflectionTestUtils.setField(reliableMessageService, "lockLeaseSeconds", 30L);
        ReflectionTestUtils.setField(reliableMessageService, "maxRetries", 3);
        ReflectionTestUtils.setField(reliableMessageService, "backoffBaseSeconds", 30);
    }

    private OrderPaidMessage buildPaidMsg() {
        return OrderPaidMessage.builder()
                .orderId(1L).orderNo("SN1").userId(10L)
                .totalAmount(new BigDecimal("100.00"))
                .couponId(null)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    // ---- savePendingMessage ----

    @Test
    @DisplayName("savePendingMessage - 写 PENDING 并返回消息 id")
    void savePendingMessage_returnsId() {
        org.mockito.Mockito.doAnswer(invocation -> {
            MessageTable m = invocation.getArgument(0);
            m.setId(99L);
            return null;
        }).when(messageTableMapper).insert(any(MessageTable.class));

        Long msgId = reliableMessageService.savePendingMessage(
                MqConstants.TOPIC_ORDER_PAID, "10", buildPaidMsg());

        then(msgId).isEqualTo(99L);
        verify(messageTableMapper).insert(any(MessageTable.class));
    }

    // ---- sendAfterCommit：无事务直调 ----

    @Test
    @DisplayName("sendAfterCommit - 发送成功必须 updateToSent（缺陷①修复）")
    void sendAfterCommit_success_updatesSent() {
        given(orderKafkaProducer.send(anyString(), anyString(), any()))
                .willReturn(CompletableFuture.completedFuture(null));

        reliableMessageService.sendAfterCommit(99L, MqConstants.TOPIC_ORDER_PAID, "10", buildPaidMsg());

        verify(orderKafkaProducer).send(eq(MqConstants.TOPIC_ORDER_PAID), eq("10"), any());
        verify(messageTableMapper).updateToSent(99L);
        verify(messageTableMapper, never()).incrementRetryCount(anyLong(), anyInt());
    }

    @Test
    @DisplayName("sendAfterCommit - 发送失败不抛异常，记重试并等待补偿")
    void sendAfterCommit_failure_retryCount() {
        given(orderKafkaProducer.send(anyString(), anyString(), any()))
                .willReturn(CompletableFuture.failedFuture(new RuntimeException("kafka down")));

        reliableMessageService.sendAfterCommit(99L, MqConstants.TOPIC_ORDER_PAID, "10", buildPaidMsg());

        verify(messageTableMapper, never()).updateToSent(anyLong());
        // maxRetries=3，首次失败 nextRetry=1 < 3 → incrementRetryCount，退避 30s × 2^0 = 30s
        verify(messageTableMapper).incrementRetryCount(99L, 30);
    }

    @Test
    @DisplayName("sendAfterCommit - 达到最大重试次数标记 FAILED")
    void sendAfterCommit_failure_markFailed() {
        given(distributedLockUtil.tryLock("msg:compensate", 0, 30, TimeUnit.SECONDS))
                .willReturn(true);
        given(orderKafkaProducer.send(anyString(), anyString(), any()))
                .willReturn(CompletableFuture.failedFuture(new RuntimeException("kafka down")));

        // 模拟补偿路径：retryCount=2 + 本次失败 → nextRetry=3 >= maxRetries=3 → FAILED
        // 注意 payload 必须是可反序列化的合法 JSON（Jackson 3 对 primitive long 字段默认 FAIL_ON_NULL_FOR_PRIMITIVES，
        // "{}" 会反序列化失败走 FAILED 分支，导致 send stub 未被使用而报 UnnecessaryStubbingException）
        MessageTable m = new MessageTable();
        m.setId(7L);
        m.setTopic(MqConstants.TOPIC_ORDER_PAID);
        m.setMessageKey("10");
        m.setPayload("{\"orderId\":1,\"orderNo\":\"SN1\",\"userId\":10,\"totalAmount\":100.00,\"timestamp\":123}");
        m.setRetryCount(2);
        m.setMaxRetries(3);

        given(messageTableMapper.findPendingForRetry()).willReturn(List.of(m));

        reliableMessageService.compensatePendingMessages();

        verify(messageTableMapper).updateToFailed(7L);
        verify(messageTableMapper, never()).updateToSent(anyLong());
    }

    // ---- 补偿任务 ----

    @Test
    @DisplayName("compensatePendingMessages - 未抢到分布式锁直接跳过")
    void compensate_lockNotAcquired_skip() {
        given(distributedLockUtil.tryLock("msg:compensate", 0, 30, TimeUnit.SECONDS))
                .willReturn(false);

        reliableMessageService.compensatePendingMessages();

        verify(messageTableMapper, never()).findPendingForRetry();
        verify(orderKafkaProducer, never()).send(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("compensatePendingMessages - 抢到锁扫描 PENDING 并发送，成功标记 SENT")
    void compensate_normalFlow() {
        given(distributedLockUtil.tryLock("msg:compensate", 0, 30, TimeUnit.SECONDS))
                .willReturn(true);
        given(orderKafkaProducer.send(anyString(), anyString(), any()))
                .willReturn(CompletableFuture.completedFuture(null));

        MessageTable m = new MessageTable();
        m.setId(5L);
        m.setTopic(MqConstants.TOPIC_ORDER_PAID);
        m.setMessageKey("10");
        m.setPayload("{\"orderId\":1,\"orderNo\":\"SN1\",\"userId\":10,\"totalAmount\":100.00,\"timestamp\":123}");
        m.setRetryCount(0);
        m.setMaxRetries(3);

        given(messageTableMapper.findPendingForRetry()).willReturn(List.of(m));

        reliableMessageService.compensatePendingMessages();

        verify(messageTableMapper).updateToSent(5L);
        verify(distributedLockUtil).unlock("msg:compensate");
    }

    @Test
    @DisplayName("compensatePendingMessages - 未知 topic 标记 FAILED")
    void compensate_unknownTopic_markFailed() {
        given(distributedLockUtil.tryLock("msg:compensate", 0, 30, TimeUnit.SECONDS))
                .willReturn(true);

        MessageTable m = new MessageTable();
        m.setId(8L);
        m.setTopic("unknown-topic");
        m.setMessageKey("10");
        m.setPayload("{}");
        m.setRetryCount(0);
        m.setMaxRetries(3);

        given(messageTableMapper.findPendingForRetry()).willReturn(List.of(m));

        reliableMessageService.compensatePendingMessages();

        verify(messageTableMapper).updateToFailed(8L);
        verify(orderKafkaProducer, never()).send(anyString(), anyString(), any());
    }
}
