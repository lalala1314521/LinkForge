package com.example.project.interceptor;


/**
 * 接口限流拦截器
 * 登录/注册接口改用 Redis 固定窗口（跨实例计数，防暴力破解/恶意注册）
 * 全局 100 QPS 保留 Guava RateLimiter 单机兜底（性能层，非安全关键）
 * 按 IP + URI 维度进行限流， 不同接口配置不同策略
 * 登录接口 /api/auth/login: 5次/60s IP 防止暴力破解
 * 注册接口 /api/users (POST): 10次/3600s 防止恶意注册
 * 其他接口 100 QPS 全局限流
 */

import com.example.project.common.ErrorCode;
import com.example.project.common.Result;
import com.example.project.util.RedisRateLimiter;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.http.MediaType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {
    private final JsonMapper objectMapper = JsonMapper.builder().build();
    private final RedisRateLimiter redisRateLimiter;

    //---- 限流阈值配置（application.yml rate-limit.* 注入，带默认值）----
    @Value("${rate-limit.login.max:5}")
    private int loginMax;
    @Value("${rate-limit.login.window:60}")
    private long loginWindow;
    @Value("${rate-limit.register.max:10}")
    private int registerMax;
    @Value("${rate-limit.register.window:3600}")
    private long registerWindow;
    @Value("${rate-limit.global.qps:100}")
    private double globalQps;

    /**
     * 通用接口限流（Guava 单机兜底，性能层非安全关键）
     */
    private final LoadingCache<String, RateLimiter> globalRateLimiters = CacheBuilder.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build(new CacheLoader<>() {
                @Override
                public RateLimiter load(String key) {
                    return RateLimiter.create(globalQps);
                }
            });

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
              throws Exception {
        String clientIp = getClientIp(request);
        String uri = request.getRequestURI();
        String method = request.getMethod();

        boolean allowed = isAllowed(clientIp, uri, method);
        if(!allowed) {
            log.warn("限流触发：IP={}, URI={}, Method={}", clientIp, uri, method);
            writeRateLimitResponse(response);
            return false;
        }
        return true;
    }

    private boolean isAllowed(String ip, String uri, String method) throws ExecutionException {
        //登录/注册为安全面接口，必须 Redis 跨实例计数
        if("/api/auth/login".equals(uri) && "POST".equalsIgnoreCase(method)) {
            return redisRateLimiter.tryAcquire("login:" + ip, loginMax, loginWindow);
        }
        if("/api/users".equals(uri) && "POST".equalsIgnoreCase(method)) {
            return redisRateLimiter.tryAcquire("register:" + ip, registerMax, registerWindow);
        }
        return globalRateLimiters.get(ip).tryAcquire();
    }

    /**
     * 返回429 + Result 格式
     */
    private void writeRateLimitResponse(HttpServletResponse response) throws IOException {
        response.setStatus(429);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String body = objectMapper.writeValueAsString(
                Result.error(ErrorCode.RATE_LIMIT_EXCEEDED));
        response.getWriter().write(body);
    }

    /**
     * 获取客户端真实IP
     * 生产环境必须由可信反向代理（Nginx）覆盖 X-Real-IP；直连开发回退 remoteAddr
     * XFF 仅兜底：攻击者可伪造，生产禁止依赖
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Real-IP");
        if(StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        // XFF 仅兜底：多级代理取第一个（注释说明：生产禁止依赖 XFF，攻击者可伪造）
        String xff = request.getHeader("X-Forwarded-For");
        if(StringUtils.hasText(xff) && !"unknown".equalsIgnoreCase(xff)) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
