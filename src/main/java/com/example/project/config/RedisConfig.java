package com.example.project.config;

import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.core.RedisTemplate;



import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis 配置（Spring Boot 4.x 简化版）
 * - 使用 Spring Boot 自动配置的 RedisTemplate
 * - CacheManager 多级 TTL（user 缓存 1h，order 缓存 15min，默认 30min）
 */
@Configuration
public class RedisConfig {

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        // Key 用 String 序列化，Value 用默认（JDK 序列化）
        // 注：缓存对象需实现 Serializable；如需 JSON 序列化，使用 JacksonRedisSerializer
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new Jackson3JsonRedisSerializer()
                ));


        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();
        cacheConfigs.put("user", defaultConfig.entryTtl(Duration.ofHours(1)));
        cacheConfigs.put("order", defaultConfig.entryTtl(Duration.ofMinutes(15)));

        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }
    // RedisTemplate Bean
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // Value 用 JSON 序列化
        template.setValueSerializer(new Jackson3JsonRedisSerializer());
        template.setHashValueSerializer(new Jackson3JsonRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

}

