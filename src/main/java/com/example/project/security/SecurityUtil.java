package com.example.project.security;

import com.example.project.common.BusinessException;
import com.example.project.common.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 安全上下文工具类
 * 从 SecurityContext 中取当前登录用户信息
 * <p>
 * JwtAuthenticationFilter 将 userId 写入 Authentication.details，
 * 权限以 ROLE_&lt;role&gt; 形式写入 authorities，本类统一从这里读取。
 */
public final class SecurityUtil {

    private SecurityUtil() {
    }

    /**
     * 获取当前登录用户ID
     *
     * @return 当前用户ID
     * @throws BusinessException 未登录或上下文异常时抛出 UNAUTHORIZED
     */
    public static Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getDetails() == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (auth.getDetails() instanceof Long userId) {
            return userId;
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }

    /**
     * 判断当前用户是否为管理员
     *
     * @return true=管理员，false=非管理员或未登录
     */
    public static boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }
}
