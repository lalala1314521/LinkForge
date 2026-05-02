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
 * 替代 OrderServiceImpl 中 ApplicationEventPublisher 的角色
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderKafkaProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * 发送订单支付成功消息
     * Key 使用 userId，保证同一用户的消息有序（分区有序性）
     */
    public void sendOrderPaid(OrderPaidMessage message) {
        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(MqConstants.TOPIC_ORDER_PAID,
                        String.valueOf(message.getUserId()),  // partition key
                        message);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("订单支付消息发送成功: topic={}, partition={}, offset={}, orderNo={}",
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        message.getOrderNo());
            } else {
                log.error("订单支付消息发送失败: orderNo={}", message.getOrderNo(), ex);
                // 生产环境可在此处做降级：写库表补偿、告警等
                throw new RuntimeException("订单支付消息发送失败", ex);  // 触发事务回滚
            }
        });
    }

    /**
     * 发送订单取消消息
     */
    public void sendOrderCancelled(OrderCancelledMessage message) {
        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(MqConstants.TOPIC_ORDER_CANCELLED,
                        String.valueOf(message.getUserId()),
                        message);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("订单取消消息发送成功: topic={}, partition={}, offset={}, orderNo={}",
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset(),
                        message.getOrderNo());
            } else {
                log.error("订单取消消息发送失败: orderNo={}", message.getOrderNo(), ex);
                throw new RuntimeException("订单取消消息发送失败", ex);
            }
        });
    }
}
