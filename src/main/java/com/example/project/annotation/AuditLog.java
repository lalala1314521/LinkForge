package com.example.project.annotation;

import java.lang.annotation.*;

/**
 * 操作审计日志注解（T2）
 * <p>
 * 标注在管理端写操作（Controller 方法）上，由 {@code AuditLogAspect} 在方法成功后记录。
 * 示例：{@code @AuditLog(action = "CREATE_PRODUCT", targetType = "PRODUCT")}
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {

    /** 操作动作，如 CREATE_PRODUCT / DELETE_USER / UPDATE_ACTIVITY_STATUS */
    String action();

    /** 目标类型，如 USER / PRODUCT / COUPON / SECKILL_ACTIVITY */
    String targetType();
}
