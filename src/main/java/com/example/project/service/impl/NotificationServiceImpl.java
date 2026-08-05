package com.example.project.service.impl;

import com.example.project.notification.NotificationProvider;
import com.example.project.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 通知服务实现（尽力而为，非关键链路：不落库、不重试）
 * <p>
 * 按配置路由到启用的 Provider（邮件/短信，默认关闭仅日志）；
 * 接运营商时：实现真实 Provider + 打开配置开关即可，调用方无感知。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final List<NotificationProvider> providers;

    @Override
    public void sendOrderPaidNotify(Long userId, String orderNo) {
        send(userId, "订单支付成功", "您的订单 " + orderNo + " 已支付成功，我们将尽快为您发货");
    }

    @Override
    public void sendOrderShippedNotify(Long userId, String orderNo) {
        send(userId, "订单已发货", "您的订单 " + orderNo + " 已发货，请留意物流信息");
    }

    @Override
    public void sendOrderCancelledNotify(Long userId, String orderNo) {
        send(userId, "订单已取消", "您的订单 " + orderNo + " 已取消，相关款项/库存已原路退回");
    }

    @Override
    public void send(Long userId, String title, String content) {
        // 路由到启用渠道（当前默认全关，仅日志）
        for (NotificationProvider provider : providers) {
            if (provider.isEnabled()) {
                try {
                    provider.send(title, content);
                } catch (Exception e) {
                    log.warn("[通知] 渠道 {} 发送失败：{}", provider.channel(), e.getMessage());
                }
            }
        }
        log.info("[通知] userId={}, title={}, content={}", userId, title, content);
    }
}
