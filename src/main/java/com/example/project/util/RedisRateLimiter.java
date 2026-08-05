package com.example.project.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Redis 固定窗口限流器
 * <p>
 * 计数逻辑：INCR 递增计数，首次递增（count==1）时设置 TTL 作为窗口期。
 * 登录/注册等安全面接口必须跨实例计数，故使用 Redis；
 * Redis 异常时降级放行（认证链路仍有 JWT/BCrypt 防线兜底）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisRateLimiter {

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 尝试获取一次访问许可
     *
     * @param key            限流维度 key（如 ip）
     * @param maxCount       窗口内最大次数
     * @param windowSeconds  窗口时长（秒）
     * @return true=放行，false=限流拒绝
     */
    public boolean tryAcquire(String key, int maxCount, long windowSeconds) {
        String redisKey = "rate:" + key;
        try {
            Long count = stringRedisTemplate.opsForValue().increment(redisKey);
            if (count != null && count == 1L) {
                //首次计数时设置过期时间，形成固定窗口
                stringRedisTemplate.expire(redisKey, windowSeconds, TimeUnit.SECONDS);
            }
            return count != null && count <= maxCount;
        } catch (Exception e) {
            //Redis 不可用时降级放行，避免限流组件自身成为故障点
            log.warn("Redis限流降级放行：key={}, error={}", redisKey, e.getMessage());
            return true;
        }
    }
}
