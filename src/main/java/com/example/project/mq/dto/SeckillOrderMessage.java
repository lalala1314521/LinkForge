package com.example.project.mq.dto;

/**
 * 秒杀订单消息（Kafka 传输 POJO）
 * <p>
 * orderNo 为幂等锚点（seckill_orders.idx_order_no 唯一键）；
 * price 为活动真实秒杀价（Consumer 落库用，杜绝 0 元单）。
 */

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeckillOrderMessage {

    private String orderNo;
    private Long userId;
    private Long activityId;
    private Long productId;
    private BigDecimal price;

    /*消息创建时间，用于排查链路延迟*/
    private long timestamp;
}
