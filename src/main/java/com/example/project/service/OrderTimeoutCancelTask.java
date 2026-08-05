package com.example.project.service;

import com.example.project.entity.Order;
import com.example.project.mapper.OrderMapper;
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
 * 订单超时关单任务（T1，普通订单）
 * <p>
 * 扫描 status='PENDING' 且 created_at &lt; now - timeout 的订单 → 逐个 {@link OrderService#cancelOrderByTimeout}。
 * 幂等三层：① Redisson 分布式锁防多实例重复扫描；② Mapper 条件更新 PENDING→CANCELLED（影响行数 0 跳过）；
 * ③ 消费端 order_process_record 幂等（重投不重复回退）。
 * <p>
 * 秒杀单超时关单（批次2 Q8）：本批次先交付普通订单；秒杀单涉及 Redis 库存回补 + SREM 用户标记 +
 * DB available_stock 回加 + seckill_orders 状态流转，且需与 ReconcileSeckillStockTask/在途消息对齐边界，
 * 复杂度高，留作后续扩展（见交付报告说明）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutCancelTask {

    private static final String TIMEOUT_CANCEL_LOCK_KEY = "order:timeout-cancel";

    private final OrderMapper orderMapper;
    private final OrderService orderService;
    private final DistributedLockUtil distributedLockUtil;

    /** 超时分钟数（默认 15 分钟），配置化 */
    @Value("${order.timeout-cancel.timeout-minutes:15}")
    private long timeoutMinutes;

    @Scheduled(fixedDelayString = "${order.timeout-cancel.fixed-delay-ms:60000}")
    public void cancelTimeoutOrders() {
        if (!distributedLockUtil.tryLock(TIMEOUT_CANCEL_LOCK_KEY, 0, 60, TimeUnit.SECONDS)) {
            log.debug("超时关单任务已被其他实例执行，跳过本次扫描");
            return;
        }
        try {
            LocalDateTime timeoutBefore = LocalDateTime.now().minusMinutes(timeoutMinutes);
            List<Order> pendingOrders = orderMapper.selectTimeoutPending(timeoutBefore);
            if (pendingOrders.isEmpty()) {
                return;
            }
            log.info("[超时关单] 扫描到待关订单 {} 笔（created_at < {}）", pendingOrders.size(), timeoutBefore);
            for (Order order : pendingOrders) {
                orderService.cancelOrderByTimeout(order.getId());
            }
        } finally {
            distributedLockUtil.unlock(TIMEOUT_CANCEL_LOCK_KEY);
        }
    }
}
