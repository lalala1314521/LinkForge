package com.example.project.common;

import lombok.Getter;

/**
 * 业务错误码枚举
 * 分布式锁，限流，慢SQL相关错误码
 */

@Getter
public enum ErrorCode {
    //HTTP标准状态码
    SUCCESS(200, "操作成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未认证，请先登录"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    INTERNAL_ERROR(500, "服务器内部错误"),

    //限流相关错误码
    RATE_LIMIT_EXCEEDED(429, "请求过于频繁，请稍后重试"),

    //用户相关错误码
    USER_NOT_FOUND(1001, "用户不存在"),
    USER_ALREADY_EXISTS(1002, "用户名已存在"),
    INVALID_PASSWORD(1003, "密码错误"),
    ACCOUNT_DISABLED(1004, "账号已禁用"),
    TOKEN_EXPIRED(1005, "Token已过期"),
    TOKEN_INVALID(1006, "Token无效"),

    //订单相关错误码
    ORDER_NOT_FOUND(1101, "订单不存在"),
    ORDER_STATUS_INVALID(1102, "订单状态不合法，无法执行该任务"),
    ORDER_CREATE_BUSY(1103, "当前请求繁忙，请勿重复提交订单"),

    //预留业务错误码
    STOCK_INSUFFICIENT(1201, "库存不足"),
    PRODUCT_NOT_FOUND(1205, "商品不存在或已下架"),
    ACTIVITY_NOT_STARTED(1202, "活动尚未开始"),
    ACTIVITY_ENDED(1203, "活动已结束"),
    USER_ALREADY_PURCHASED(1204, "您已购买过该商品"),

    // 优惠券模块
    COUPON_NOT_FOUND(1301, "优惠券不存在"),
    COUPON_ALREADY_USED(1302, "优惠券已使用"),
    COUPON_EXPIRED(1303, "优惠券已过期"),
    COUPON_NOT_APPLICABLE(1304, "优惠券不满足使用条件"),
    COUPON_ALREADY_CLAIMED(1305, "优惠券已领取，每人限领一张"),
    COUPON_STOCK_RUN_OUT(1306, "优惠券已被领完"),

    // 积分模块
    POINTS_NOT_ENOUGH(1311, "积分不足"),
    POINTS_EARN_FAILED(1312, "积分发放失败，请稍后重试"),

    // 秒杀模块
    SECKILL_REPEAT(1321, "您已参与过该秒杀活动"),
    SECKILL_STOCK_EMPTY(1322, "秒杀商品已抢光"),
    SEC_ACTIVITY_NOT_STARTED(1323, "秒杀活动未开始"),
    SEC_ACTIVITY_ENDED(1324, "秒杀活动已结束"),
    SECKILL_ACTIVITY_NOT_FOUND(1325, "秒杀活动不存在"),
    SECKILL_STATUS_INVALID(1326, "秒杀活动状态不合法"),

    // 通知模块
    NOTIFICATION_SEND_FAILED(1331, "通知发送失败"),

    // 消息表模块
    MESSAGE_SEND_MAX_RETRY(1341, "消息发送超过最大重试次数");

    private final int code;
    private final String message;
    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
