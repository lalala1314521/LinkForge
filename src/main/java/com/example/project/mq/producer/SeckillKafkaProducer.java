package com.example.project.mq.producer;

import com.example.project.mq.MqConstants;
import com.example.project.mq.dto.SeckillOrderMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 秒杀消息生产者（D6：直发，不写本地消息表）
 * <p>
 * 热路径不碰 DB：秒杀抢购成功后直接 Kafka 异步发送（key=userId 分区有序）。
 * 消息丢失由对账任务兜底（Redis 已扣 vs DB 未落单 → 差异告警/补单）。
 * 异步回调只记日志、不抛异常（可靠性交给对账，不在回调里破坏调用方）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillKafkaProducer {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendSeckillOrder(SeckillOrderMessage msg) {
        kafkaTemplate.send(MqConstants.TOPIC_SECKILL_ORDER, String.valueOf(msg.getUserId()), msg)
                .whenComplete((res, ex) -> {
                    if (ex == null) {
                        log.info("[秒杀] 消息发送成功: orderNo={}, partition={}, offset={}",
                                msg.getOrderNo(),
                                res.getRecordMetadata().partition(),
                                res.getRecordMetadata().offset());
                    } else {
                        log.error("[秒杀] 消息发送失败，等待对账兜底: orderNo={}", msg.getOrderNo(), ex);
                    }
                });
    }
}
