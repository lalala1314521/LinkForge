package com.example.project.mq;

/**
 * 消息队列Topic & Group 常量集中管理
 */
public final class MqConstants {

    private MqConstants() {};

    //订单领域
    public static final String TOPIC_ORDER_PAID = "order-paid";
    public static final String TOPIC_ORDER_CANCELLED = "order_cancelled";

    //消费组
    public static final String GROUP_ORDER_NOTIFICATION = "order-notification-group";
}