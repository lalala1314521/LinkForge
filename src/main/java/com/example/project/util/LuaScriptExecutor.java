package com.example.project.util;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Lua 脚本执行器（秒杀库存预扣 + 用户去重）
 * <p>
 * ⚠️ 兼容性关键点：必须用 StringRedisTemplate（String 序列化）。
 * 项目 RedisTemplate&lt;String,Object&gt; 使用 Jackson3JsonRedisSerializer（带 @class 类型头），
 * 会把库存值序列化成带类型信息的 JSON，Lua 的 tonumber 无法解析 → 直接炸。
 * StringRedisTemplate 是 Spring Boot 自动配置（String 序列化），key/value 干净。
 * <p>
 * 返回码：1 = 抢购成功；-1 = 重复抢购；-2 = 售罄 / key 缺失（fail-closed，不回填 DB）。
 */
@Component
@RequiredArgsConstructor
public class LuaScriptExecutor {

    private final StringRedisTemplate stringRedisTemplate;

    private final DefaultRedisScript<Long> seckillScript = buildSeckillScript();

    /**
     * 构建脚本（无参构造 + setLocation/setResultType，兼容不同 Spring Data Redis 版本的构造器差异）
     */
    private static DefaultRedisScript<Long> buildSeckillScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/seckill_decrement_stock.lua"));
        script.setResultType(Long.class);
        return script;
    }

    /**
     * 原子执行「库存预扣 + 用户去重」
     *
     * @return 1 成功 / -1 重复 / -2 售罄或 key 缺失
     */
    public long executeSeckillDecrement(Long activityId, Long userId) {
        Long result = stringRedisTemplate.execute(seckillScript,
                List.of("seckill:stock:" + activityId, "seckill:users:" + activityId),
                String.valueOf(userId));
        // Redis 异常/脚本未执行 → null → fail-closed 视为售罄
        return result == null ? -2 : result;
    }
}
