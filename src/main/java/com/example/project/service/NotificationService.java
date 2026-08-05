package com.example.project.service;

/**
 * 通知服务接口
 * <p>
 * 本批次仅日志实现（@Slf4j），预留短信/邮件/站内信扩展点：
 * 未来接入外部渠道时新增 Impl 替换即可，调用方（OrderEventProcessor）无感知。
 */
public interface NotificationService {

    /**
     * 订单支付成功通知
     */
    void sendOrderPaidNotify(Long userId, String orderNo);

    /**
     * 订单取消通知
     */
    void sendOrderCancelledNotify(Long userId, String orderNo);

    /**
     * 通用通知扩展点
     */
    void send(Long userId, String title, String content);
}
