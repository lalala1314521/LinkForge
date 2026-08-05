package com.example.project.config;

import lombok.RequiredArgsConstructor;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * 布隆过滤器提供者（D5，修 §3.7 写死容量缺陷）
 * <p>
 * - 容量/误判率参数化：seckill.bloom.expected-insertions / false-probability
 * - tryInit 只在首次初始化生效（Redisson 特性）；容量变更需新活动新 key
 * - key 命名：seckill:bloom:{activityId}
 * <p>
 * 作用：请求入口 contains(userId) 快速拦截疑似重复用户（防刷，少打 Redis）；
 * 权威去重仍在 Lua 的 SADD（布隆误判仅误伤极小比例真实用户，误判率 0.001 可接受）。
 */
@Configuration
@RequiredArgsConstructor
public class BloomFilterConfig {

    private final RedissonClient redissonClient;

    @Value("${seckill.bloom.expected-insertions:100000}")
    private long expectedInsertions;

    @Value("${seckill.bloom.false-probability:0.001}")
    private double falseProbability;

    /**
     * 取活动专属布隆过滤器；未初始化时自动 tryInit（幂等，仅首次生效）
     */
    public RBloomFilter<String> getBloomFilter(Long activityId) {
        RBloomFilter<String> filter = redissonClient.getBloomFilter("seckill:bloom:" + activityId);
        filter.tryInit(expectedInsertions, falseProbability);
        return filter;
    }
}
