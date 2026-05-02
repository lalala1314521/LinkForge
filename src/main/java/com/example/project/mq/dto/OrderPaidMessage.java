package com.example.project.mq.dto;

/**
 * 订单支付成功消息
 * 替代OrderPaidEvent，作为跨进程传输的纯 POJO
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
public class OrderPaidMessage {

    private Long orderId;
    private String orderNo;
    private Long userId;
    private BigDecimal totalAmount;

    /*消息创建时间， 用于排查链路延迟*/
    private long timestamp;
}