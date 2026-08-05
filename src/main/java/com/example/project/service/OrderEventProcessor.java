package com.example.project.service;

import com.example.project.mq.dto.OrderCancelledMessage;
import com.example.project.mq.dto.OrderPaidMessage;

/**
 * 订单事件处理器接口
 * <p>
 * @Transactional 必须写在实现类方法上（经 Spring 代理调用才生效），
 * 不要写在 @KafkaListener 方法上 —— 容器直接反射调用 Listener 方法，不走 AOP 代理。
 */
public interface OrderEventProcessor {

    /**
     * 处理订单支付成功事件（幂等：order_process_record 先 INSERT 占位）
     */
    void processPaid(OrderPaidMessage message);

    /**
     * 处理订单取消事件（幂等同上）
     */
    void processCancelled(OrderCancelledMessage message);
}
