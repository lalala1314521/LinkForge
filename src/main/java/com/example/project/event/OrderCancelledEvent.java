package com.example.project.event;

/**
 * 订单取消事件 异步事件处理
 */

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class OrderCancelledEvent extends ApplicationEvent {

    private final Long orderId;
    private final String orderNo;
    private final Long userId;

    public OrderCancelledEvent(Object source, Long orderId, String orderNo, Long userId) {
        super(source);
        this.orderId = orderId;
        this.orderNo = orderNo;
        this.userId = userId;
    }
}