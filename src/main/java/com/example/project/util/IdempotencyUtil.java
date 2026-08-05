package com.example.project.util;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 接口幂等工具（T3，下单接口重点）
 * <p>
 * 客户端传 Idempotency-Key 请求头，服务端用 Redis SETNX 去重：
 * - key = idempotency:{userId}:{key}，TTL 默认 5min（配置化，防内存/Redis 泄漏）
 * - 首次请求 SETNX 成功返回 true；重复请求键已存在返回 false → 拒绝（抛 ORDER_CREATE_BUSY）
 * <p>
 * 层次说明：幂等键只做"重复提交防护"（请求层去重），
 * 最终一致性仍靠业务幂等（本地消息表 PENDING→SENT、seckill_orders/order_process_record 唯一键）。
 * 与 createOrder 既有 5s 用户维度分布式锁并存不冲突：锁防"同用户并发重复提交"，幂等键防"同 key 重复请求"。
 */
@Component
@RequiredArgsConstructor
public class IdempotencyUtil {

    private final StringRedisTemplate stringRedisTemplate;

    @Value("${idempotency.ttl-seconds:300}")
    private long ttlSeconds;

    /**
     * @return true = 可继续（无 key 或键不存在）；false = 重复请求（键已存在）
     */
    public boolean tryAcquire(Long userId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return true;   // 未传幂等键不拦截（兼容旧客户端）
        }
        String redisKey = "idempotency:" + userId + ":" + idempotencyKey;
        Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(redisKey, "1", ttlSeconds, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(acquired);
    }
}
