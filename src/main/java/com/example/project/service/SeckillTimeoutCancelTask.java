package com.example.project.service;

import com.example.project.entity.SeckillOrder;
import com.example.project.mapper.SeckillOrderMapper;
import com.example.project.util.DistributedLockUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀订单超时关单任务
 * <p>
 * 扫描 status='PENDING' 且 created_at &lt; now - timeout 的秒杀单 → 逐个 {@link SeckillService#cancelSeckillOrderByTimeout}。
 * 幂等三层：① Redisson 分布式锁防多实例重复扫描；② Mapper 条件更新 PENDING→CANCELLED（影响行数 0 跳过）；
 * ③ 回库存 DB 条件更新（available_stock &lt; total_stock）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillTimeoutCancelTask {

    private static final String TIMEOUT_CANCEL_LOCK_KEY = "seckill:timeout-cancel";

    private final SeckillOrderMapper seckillOrderMapper;
    private final SeckillService seckillService;
    private final DistributedLockUtil distributedLockUtil;

    /** 超时分钟数（默认 15 分钟），配置化 */
    @Value("${seckill.timeout-cancel.timeout-minutes:15}")
    private long timeoutMinutes;

    @Scheduled(fixedDelayString = "${seckill.timeout-cancel.fixed-delay-ms:60000}")
    public void cancelTimeoutSeckillOrders() {
        if (!distributedLockUtil.tryLock(TIMEOUT_CANCEL_LOCK_KEY, 0, 60, TimeUnit.SECONDS)) {
            log.debug("秒杀超时关单任务已被其他实例执行，跳过本次扫描");
            return;
        }
        try {
            LocalDateTime timeoutBefore = LocalDateTime.now().minusMinutes(timeoutMinutes);
            List<SeckillOrder> pendingOrders = seckillOrderMapper.selectTimeoutPending(timeoutBefore);
            if (pendingOrders.isEmpty()) {
                return;
            }
            log.info("[秒杀超时关单] 扫描到待关秒杀单 {} 笔（created_at < {}）", pendingOrders.size(), timeoutBefore);
            for (SeckillOrder order : pendingOrders) {
                seckillService.cancelSeckillOrderByTimeout(order.getOrderNo());
            }
        } finally {
            distributedLockUtil.unlock(TIMEOUT_CANCEL_LOCK_KEY);
        }
    }
}
