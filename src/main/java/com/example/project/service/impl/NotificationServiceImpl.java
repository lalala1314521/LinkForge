package com.example.project.service.impl;

import com.example.project.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 通知服务日志实现（尽力而为，非关键链路：不落库、不重试）
 * <p>
 * 预留：未来接短信/邮件/站内信，新增 Impl 替换即可，Consumer 无感知。
 */
@Slf4j
@Service
public class NotificationServiceImpl implements NotificationService {

    @Override
    public void sendOrderPaidNotify(Long userId, String orderNo) {
        send(userId, "订单支付成功", "您的订单 " + orderNo + " 已支付成功，我们将尽快为您发货");
    }

    @Override
    public void sendOrderCancelledNotify(Long userId, String orderNo) {
        send(userId, "订单已取消", "您的订单 " + orderNo + " 已取消，相关款项/库存已原路退回");
    }

    @Override
    public void send(Long userId, String title, String content) {
        log.info("[通知] userId={}, title={}, content={}", userId, title, content);
    }
}
