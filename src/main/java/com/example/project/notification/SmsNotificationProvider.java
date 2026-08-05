package com.example.project.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 短信通知 Provider（占位实现）
 * <p>
 * 预留：接短信服务商（如腾讯云 SMS/阿里云短信）时，在此实现真实发送逻辑，
 * 并将 application.yml 的 notification.sms.enabled 置为 true。
 */
@Slf4j
@Component
public class SmsNotificationProvider implements NotificationProvider {

    @Value("${notification.sms.enabled:false}")
    private boolean enabled;

    @Value("${notification.sms.sign:LinkForge}")
    private String sign;

    @Override
    public String channel() {
        return "sms";
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void send(String title, String content) {
        // TODO 接入短信服务商：构造短信（sign=sign, body=content）并发送
        log.info("[短信通知][预留占位] sign={}, content={}", sign, content);
    }
}
