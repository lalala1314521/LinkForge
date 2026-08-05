package com.example.project.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 邮件通知 Provider（占位实现）
 * <p>
 * 预留：接邮件服务商（如 SMTP/腾讯云 SES）时，在此实现真实发送逻辑，
 * 并将 application.yml 的 notification.email.enabled 置为 true。
 */
@Slf4j
@Component
public class EmailNotificationProvider implements NotificationProvider {

    @Value("${notification.email.enabled:false}")
    private boolean enabled;

    @Value("${notification.email.from:linkforge@example.com}")
    private String from;

    @Override
    public String channel() {
        return "email";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void send(String title, String content) {
        // TODO 接入邮件服务商：构造邮件（from=from, subject=title, body=content）并发送
        log.info("[邮件通知][预留占位] from={}, title={}, content={}", from, title, content);
    }
}
