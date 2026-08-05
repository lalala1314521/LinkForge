package com.example.project.service.impl;

import com.example.project.common.BusinessException;
import com.example.project.common.ErrorCode;
import com.example.project.entity.SeckillActivity;
import com.example.project.entity.SeckillOrder;
import com.example.project.mapper.SeckillActivityMapper;
import com.example.project.mapper.SeckillOrderMapper;
import com.example.project.mq.dto.SeckillOrderMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 秒杀订单处理器（消费真实逻辑，D7 幂等）
 * <p>
 * 幂等锚点：seckill_orders.idx_order_no 唯一键 —— 先 INSERT 占位，
 * 重复 order_no → DuplicateKeyException → 跳过（ACK）。
 * 同事务内条件扣减 DB available_stock（防 DB 超卖兜底）。
 * <p>
 * @Transactional 写在处理器上（经 Spring 代理调用），@KafkaListener 只做薄壳。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillOrderProcessor {

    private final SeckillOrderMapper seckillOrderMapper;
    private final SeckillActivityMapper seckillActivityMapper;

    @Transactional(rollbackFor = Exception.class)
    public void process(SeckillOrderMessage msg) {
        // 活动存在性校验（防脏消息；异常 → 不 ACK → 重投）
        SeckillActivity activity = seckillActivityMapper.selectById(msg.getActivityId());
        if (activity == null) {
            throw new BusinessException(ErrorCode.SECKILL_ACTIVITY_NOT_FOUND);
        }

        // 幂等锚点：INSERT 占位，重复即跳过
        try {
            SeckillOrder order = new SeckillOrder();
            order.setOrderNo(msg.getOrderNo());
            order.setUserId(msg.getUserId());
            order.setActivityId(msg.getActivityId());
            order.setProductId(msg.getProductId());
            order.setPrice(msg.getPrice());        // 活动真实秒杀价（D2）
            order.setStatus("PENDING");            // D3：已抢到待支付
            seckillOrderMapper.insert(order);
        } catch (DuplicateKeyException e) {
            log.info("[秒杀幂等] 订单{} 已落库，跳过", msg.getOrderNo());
            return;
        }

        // DB 库存最终扣减（条件更新，防 DB 超卖兜底）
        int affected = seckillActivityMapper.decrementAvailableStock(msg.getActivityId());
        if (affected == 0) {
            // Redis 已预扣限流，DB 理论足够；若不足说明数据不一致，WARN 交对账任务检测
            log.warn("[秒杀消费] 订单{} DB 库存扣减失败（available_stock<=0），请检查活动{}",
                    msg.getOrderNo(), msg.getActivityId());
        }
        log.info("[秒杀消费] 订单{} 落库成功，price={}", msg.getOrderNo(), msg.getPrice());
    }
}
