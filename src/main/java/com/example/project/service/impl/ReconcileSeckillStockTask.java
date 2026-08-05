package com.example.project.service.impl;

import com.example.project.entity.SeckillActivity;
import com.example.project.mapper.SeckillActivityMapper;
import com.example.project.mapper.SeckillOrderMapper;
import com.example.project.util.DistributedLockUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀库存对账任务（D10，修 §3.6 竞态）
 * <p>
 * 纪律：
 * - 只对 <b>ENDED</b> 活动"以 DB 为准清理 Redis"（此时消息已全部消费、DB 收敛）；
 * - <b>ACTIVE</b> 活动缺失 key 只告警、绝不回灌 DB（DB 异步落库必然滞后，回灌=回涨=超卖）；
 * - 同时检测"Redis 已售 vs DB 已落单"差异（消息丢失 → 日志告警人工补单）。
 * <p>
 * 分布式锁防多实例重复扫描。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReconcileSeckillStockTask {

    private static final String RECONCILE_LOCK_KEY = "seckill:reconcile";

    private final SeckillActivityMapper seckillActivityMapper;
    private final SeckillOrderMapper seckillOrderMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final DistributedLockUtil distributedLockUtil;

    @Scheduled(fixedDelayString = "${seckill.reconcile.fixed-delay-ms:60000}")
    public void reconcile() {
        if (!distributedLockUtil.tryLock(RECONCILE_LOCK_KEY, 0, 60, TimeUnit.SECONDS)) {
            log.debug("对账任务已被其他实例执行，跳过本次扫描");
            return;
        }
        try {
            reconcileEndedActivities();
            reconcileActiveActivities();
        } finally {
            distributedLockUtil.unlock(RECONCILE_LOCK_KEY);
        }
    }

    /**
     * ① ENDED 活动：以 DB 为准清理 Redis；检测消息丢失（Redis 已售 vs DB 已落单）
     */
    private void reconcileEndedActivities() {
        for (SeckillActivity activity : seckillActivityMapper.selectByStatus("ENDED")) {
            String stockKey = "seckill:stock:" + activity.getId();
            String usersKey = "seckill:users:" + activity.getId();

            // 先读后删（删了就取不到剩余值了）
            Long redisRemain = Optional.ofNullable(stringRedisTemplate.opsForValue().get(stockKey))
                    .map(Long::valueOf)
                    .orElse(null);
            long dbSold = seckillOrderMapper.countByActivityId(activity.getId());
            if (redisRemain != null && (long) activity.getTotalStock() - redisRemain != dbSold) {
                log.warn("[对账] 活动{} 存在丢失消息：Redis已售={}, DB已落单={}", activity.getId(),
                        (long) activity.getTotalStock() - redisRemain, dbSold);
            }
            stringRedisTemplate.delete(stockKey);
            stringRedisTemplate.delete(usersKey);
            log.info("[对账] 活动{} 已 ENDED，清理 Redis 库存/用户集合", activity.getId());
        }
    }

    /**
     * ② ACTIVE 活动：缺失 key 只告警、不回灌 DB（防在途消息回涨超卖）
     */
    private void reconcileActiveActivities() {
        for (SeckillActivity activity : seckillActivityMapper.selectByStatus("ACTIVE")) {
            if (!Boolean.TRUE.equals(stringRedisTemplate.hasKey("seckill:stock:" + activity.getId()))) {
                log.error("[对账] 活动{} ACTIVE 但库存 key 丢失，已 fail-closed，请人工 re-warm", activity.getId());
            }
        }
    }
}
