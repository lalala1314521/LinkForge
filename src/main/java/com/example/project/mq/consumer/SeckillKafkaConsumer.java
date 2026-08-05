package com.example.project.mq.consumer;

import com.example.project.mq.dto.SeckillOrderMessage;
import com.example.project.service.impl.SeckillOrderProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * 秒杀订单 Kafka 消费者（薄壳）
 * <p>
 * 只做：解析 + 委托 {@link SeckillOrderProcessor} + ACK。
 * @Transactional 写在处理器上（经 Spring 代理调用），不写在 @KafkaListener 方法上。
 * 失败不 ACK → 重投 → seckill_orders.idx_order_no 唯一键保证不重复落库。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillKafkaConsumer {

    private final SeckillOrderProcessor seckillOrderProcessor;

    @KafkaListener(
            topics = "${seckill.topic.order:seckill-order}",
            groupId = "seckill-group"
    )
    public void handleSeckillOrder(@Payload SeckillOrderMessage msg, Acknowledgment ack) {
        try {
            seckillOrderProcessor.process(msg);   // 事务在处理器内
            ack.acknowledge();
        } catch (Exception e) {
            log.error("[秒杀消费] 处理失败：orderNo={}", msg.getOrderNo(), e);
            // 不 ack → 重投 → 幂等锚点保证不重复
        }
    }
}
