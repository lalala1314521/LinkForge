package  com.example.project.common;

import  lombok.Getter;

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
    ACTIVITY_NOT_STARTED(1202, "活动尚未开始"),
    ACTIVITY_ENDED(1203, "活动已结束"),
    USER_ALREADY_PURCHASED(1204, "您已购买过该商品");

    private final int code;
    private final String message;
    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
