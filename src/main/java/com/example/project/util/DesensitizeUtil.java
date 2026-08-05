package com.example.project.util;

/**
 * 敏感信息脱敏工具类
 * 统一对手机号、邮箱做脱敏处理（管理端同样脱敏，纵深防御）
 */
public final class DesensitizeUtil {

    private DesensitizeUtil() {
    }

    /**
     * 手机号脱敏：138****8000
     * 长度不足 7 位时原样返回（无法脱敏则不强改）
     */
    public static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2");
    }

    /**
     * 邮箱脱敏：a***e@example.com
     * 用户名 ≤2 位时只保留首字符；不含 @ 时原样返回；
     * 邮箱名前缀为空（如 "@example.com"）时原样返回（防御性，不抛异常）
     */
    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return email;
        }
        int atIndex = email.indexOf('@');
        String name = email.substring(0, atIndex);
        String domain = email.substring(atIndex + 1);
        if (name.isEmpty()) {
            return email;
        }
        String masked;
        if (name.length() == 1) {
            masked = name + "***";
        } else if (name.length() == 2) {
            masked = name.charAt(0) + "***";
        } else {
            masked = name.substring(0, 1) + "***" + name.substring(name.length() - 1);
        }
        return masked + "@" + domain;
    }
}
