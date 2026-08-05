-- 秒杀库存预扣 + 用户去重（原子完成，D4/D8）
-- KEYS[1] seckill:stock:{activityId}   String：剩余库存（预热写入，TTL 覆盖活动期）
-- KEYS[2] seckill:users:{activityId}   Set：已购用户（SADD 去重 = 限购 1 件）
-- ARGV[1] userId
-- 返回码： 1 = 抢购成功；-1 = 重复抢购；-2 = 售罄 / key 缺失（fail-closed，绝不回填 DB）

if redis.call('SISMEMBER', KEYS[2], ARGV[1]) == 1 then
    return -1
end

local stock = tonumber(redis.call('GET', KEYS[1]) or '-1')
if stock < 0 then
    -- key 不存在（未预热/已过期/Redis 重启丢失）→ 视为售罄，不回填 DB（回填即超卖）
    return -2
end
if stock <= 0 then
    return -2
end

redis.call('DECR', KEYS[1])
redis.call('SADD', KEYS[2], ARGV[1])
return 1
