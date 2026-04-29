package com.example.project.interceptor;


/**
 * 接口限流拦截器基于 Guava RatrLimiter + LoadingCache
 * 按 IP + URI 维度进行限流， 不同接口配置不同策略
 * 登录接口 /api/auth/login. 5次每分钟 IP 防止暴力破解
 * 注册接口 /api/users (POST): 10次每小时 放置恶意注册
 * 其他接口 100 QPS 全局限流
 */

import com.example.project.common.ErrorCode;
import com.example.project.common.Result;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.http.MediaType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class RateLimitInterceptor implements HandlerInterceptor {
    private final JsonMapper objectMapper = JsonMapper.builder().build();

    /**
     * 登录接口限流 Cache :key=IP, value = RateLimiter 5 / 60 = 0.0833 QPS
     */
    private final LoadingCache<String, RateLimiter> loginRateLimiters = CacheBuilder.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build(new CacheLoader<>() {
                @Override
                public RateLimiter load(String key) {
                    return RateLimiter.create(5.0 / 60.0);
                }
            });

    private final LoadingCache<String, RateLimiter> registerRateLimiters = CacheBuilder.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(2, TimeUnit.HOURS)
            .build(new CacheLoader<>() {
                @Override
                public RateLimiter load(String key) {
                    return RateLimiter.create(10.0 / 3600.0);
                }
            });
    /**
     * 通用接口限流
     */
    private final LoadingCache<String, RateLimiter> globalRateLimiters = CacheBuilder.newBuilder()
            .maximumSize(10_000)
            .expireAfterAccess(1, TimeUnit.HOURS)
            .build(new CacheLoader<>() {
                @Override
                public RateLimiter load(String key) {
                    return RateLimiter.create(100.0);
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
        if("/api/auth/login".equals(uri) && "POST".equalsIgnoreCase(method)) {
            return loginRateLimiters.get(ip).tryAcquire();
        }
        if("/api/users".equals(uri) && "POST".equalsIgnoreCase(method)) {
            return registerRateLimiters.get(ip).tryAcquire();
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
     * 获取客户端真实IP 兼容反向代理
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if(ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            //多级代理时获取第一个IP
            return ip.split(",")[0].trim();
        }
        ip = request.getHeader("X-Real_IP");
        if(ip != null && !ip.isEmpty() && !"unknow".equalsIgnoreCase(ip)){
            return ip;
        }
        return request.getRemoteAddr();
    }
}