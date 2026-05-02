package com.example.project.mq.consumer;

import com.example.project.mq.dto.OrderCancelledMessage;
import com.example.project.mq.dto.OrderPaidMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * 订单事件 Kafka 消费者
 * 替代原 OrderEventListener 的 @Async @EventListener
 *
 * 关键改进点 vs 原 Spring Event：
 * 1. 消息持久化到 Kafka Broker，JVM 崩溃不丢消息
 * 2. 手动 ACK，处理完才确认，异常时消息会重投递
 * 3. 可被其他微服务消费（跨进程解耦）
 */
@Slf4j
@Component
public class OrderKafkaConsumer {

    /**
     * 消费订单支付成功消息
     * containerFactory 用默认即可，因为 application.yml 已经配好了
     */
    @KafkaListener(
            topics = "${order.topic.paid:order-paid}",     // 支持配置覆盖
            groupId = "order-notification-group"
    )
    public void handleOrderPaid(@Payload OrderPaidMessage message,
                                Acknowledgment ack) {
        try {
            long consumeStart = System.currentTimeMillis();
            log.info("[Kafka] 收到订单支付消息：orderId={}, orderNo={}, userId={}, amount={}, msgAge={}ms",
                    message.getOrderId(), message.getOrderNo(), message.getUserId(),
                    message.getTotalAmount(),
                    consumeStart - message.getTimestamp());

            // TODO: 调用仓库服务备货
            log.info("[Kafka] 通知仓库服务：准备发货，订单={}", message.getOrderNo());
            // TODO: 发送支付成功短信
            log.info("[Kafka] 发送支付成功短信：用户={}, 订单={}", message.getUserId(), message.getOrderNo());
            // TODO: 更新会员积分
            log.info("[Kafka] 更新会员积分：用户={}, 金额={}", message.getUserId(), message.getTotalAmount());


            long cost = System.currentTimeMillis() - consumeStart;
            log.info("[Kafka] 订单{} 下游通知处理完成，耗时={}ms", message.getOrderNo(), cost);

            // 手动确认，消息才会被标记为已消费
            ack.acknowledge();

        } catch (Exception e) {
            log.error("[Kafka] 订单支付消息处理异常：orderNo={}", message.getOrderNo(), e);
            // 不调用 ack.acknowledge()，Kafka 会重新投递该消息
            // 配合 retry + 死信队列使用效果更佳
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
            long consumeStart = System.currentTimeMillis();
            log.info("[Kafka] 收到订单取消消息：orderId={}, orderNo={}, userId={}, msgAge={}ms",
                    message.getOrderId(), message.getOrderNo(), message.getUserId(),
                    consumeStart - message.getTimestamp());


            // TODO: 回退库存
            log.info("[Kafka] 回退库存：订单={}", message.getOrderNo());
            // TODO: 解冻优惠券
            log.info("[Kafka] 解冻优惠券：订单={}", message.getOrderNo());


            log.info("[Kafka] 订单{} 回滚操作完成", message.getOrderNo());
            ack.acknowledge();

        } catch (Exception e) {
            log.error("[Kafka] 订单取消消息处理异常：orderNo={}", message.getOrderNo(), e);
            // 不 ack → 消息重投递
        }
    }
}
