package com.example.project.notification;

/**
 * 通知渠道 Provider 抽象（预留邮箱/短信扩展）
 * <p>
 * 当前内置 Email/Sms 占位实现（仅日志）；接运营商时新增 Provider 实现并开启配置开关即可，
 * 调用方（NotificationService）无感知。
 */
public interface NotificationProvider {

    /** 渠道名称（email / sms） */
    String channel();

    /** 是否启用（由配置开关控制，默认关闭） */
    boolean isEnabled();

    /** 发送通知 */
    void send(String title, String content);
}
