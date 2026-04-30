package com.example.project.event;

/**
 * 订单支付成功事件 异步事件处理
 */

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.math.BigDecimal;

@Getter
public class OrderPaidEvent extends ApplicationEvent {

    private final Long orderId;
    private final String orderNo;
    private final Long userId;
    private final BigDecimal totalAmount;

    public OrderPaidEvent(Object source, Long orderId, String orderNo, Long userId, BigDecimal totalAmount) {
        super(source);
        this.orderId = orderId;
        this.orderNo = orderNo;
        this.userId = userId;
        this.totalAmount = totalAmount;
    }
}