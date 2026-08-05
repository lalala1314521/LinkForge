package com.example.project.mq.dto;

/**
 * 订单取消消息
 * 替代OrderCancelledEvent
 */

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCancelledMessage {

    private Long orderId;
    private String orderNo;
    private Long userId;

    /** 使用的用户优惠券实例ID（user_coupons.id），无券为 null */
    private Long couponId;

    /*消息创建时间*/
    private long timestamp;
}