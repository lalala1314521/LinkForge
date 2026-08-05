package com.example.project.mq.consumer;

import com.example.project.mq.dto.OrderCancelledMessage;
import com.example.project.mq.dto.OrderPaidMessage;
import com.example.project.service.OrderEventProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * 订单事件 Kafka 消费者（薄壳）
 * <p>
 * 只做：解析 + 委托 {@link OrderEventProcessor} + ACK。
 * @Transactional 写在 Processor 实现类上（经 Spring 代理调用），
 * 不写在 @KafkaListener 方法上（容器直接反射调用、不走 AOP 代理，@Transactional 不生效）。
 * <p>
 * 失败不 ACK → Kafka 重投递 → order_process_record 幂等表保证不重复生效。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderKafkaConsumer {

    private final OrderEventProcessor orderEventProcessor;

    /**
     * 消费订单支付成功消息
     */
    @KafkaListener(
            topics = "${order.topic.paid:order-paid}",     // 支持配置覆盖
            groupId = "order-notification-group"
    )
    public void handleOrderPaid(@Payload OrderPaidMessage message,
                                Acknowledgment ack) {
        try {
            orderEventProcessor.processPaid(message);   // 事务在处理器内
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[Kafka] 支付消息处理失败：orderNo={}", message.getOrderNo(), e);
            // 不 ack → 重投递 → 幂等表保证不重复执行
        }
    }

    /**
     * 消费订单取消消息
     */
    @KafkaListener(
            topics = "${order.topic.cancelled:order-cancelled}",
            groupId = "order-notification-group"
    )
    public void handleOrderCancelled(@Payload OrderCancelledMessage message,
                                     Acknowledgment ack) {
        try {
            orderEventProcessor.processCancelled(message);   // 事务在处理器内
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[Kafka] 取消消息处理失败：orderNo={}", message.getOrderNo(), e);
            // 不 ack → 消息重投递
        }
    }
}
