package com.example.project.listener;

/**
 * 订单事件监听器 异步事件处理
 * 监听订单状态变更事件并异步执行下游通知逻辑
 */

import com.example.project.event.OrderCancelledEvent;
import com.example.project.event.OrderPaidEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OrderEventListener {

    /**
     * 异步处理订单支付成功事件 -模拟通知下游服务
     */
    @Async
    @EventListener
    public void handleOrderPaid(OrderPaidEvent event) {
        log.info("[异步]订单支付成功通知：orderId={}, orderNo={},userId={},amount={}",
                event.getOrderId(), event.getOrderNo(), event.getUserId(), event.getTotalAmount());
        //模拟通知下游仓库备货，发送支付成功短信，更新积分等
        log.info("[异步] 订单{} 下游通知处理完成", event.getOrderNo());
    }

    /**
     * 异步处理订单取消事件-模拟回滚库存
     */
    @Async
    @EventListener
    public void handleOrderCancelled(OrderCancelledEvent event) {
        log.info("[异步] 订单取消， 回滚业务：orderId={}, orderNo={}, userId={}",
                event.getOrderId(), event.getOrderNo(), event.getUserId());
        //模拟 回退库存。解冻优惠卷等操作
        log.info("[异步] 订单{} 回滚操作完成",event.getOrderNo());
    }
}