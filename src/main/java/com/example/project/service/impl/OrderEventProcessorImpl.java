package com.example.project.service.impl;

import com.example.project.common.BusinessException;
import com.example.project.common.ErrorCode;
import com.example.project.entity.Order;
import com.example.project.entity.OrderProcessRecord;
import com.example.project.mapper.OrderMapper;
import com.example.project.mapper.OrderProcessRecordMapper;
import com.example.project.mq.dto.OrderCancelledMessage;
import com.example.project.mq.dto.OrderPaidMessage;
import com.example.project.service.CouponService;
import com.example.project.service.InventoryService;
import com.example.project.service.NotificationService;
import com.example.project.service.OrderEventProcessor;
import com.example.project.service.PointsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 订单事件处理器实现 —— 消费真实逻辑 + 幂等（D1/D2）
 * <p>
 * 幂等：处理前先 INSERT order_process_record 占位（order_no + event_type 唯一键），
 * 重复投递 → DuplicateKeyException → 跳过；失败整体回滚 → 重投递后完整重跑，
 * 杜绝"处理一半重投再处理一次"。
 * <p>
 * 库存语义（下单预扣）：createOrder 事务内已预扣库存，支付消费端不再扣减（避免双扣 -2qty）；
 * 取消消费端回退库存（restoreByOrder），受本方法事务 + 幂等占位保护（incrementStock 无条件自增，
 * 脱离幂等表重复执行会多加库存）。
 * <p>
 * 注意：@Transactional 写在 Processor 上（经 Spring 代理调用），不是 @KafkaListener 方法上。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderEventProcessorImpl implements OrderEventProcessor {

    private final OrderProcessRecordMapper orderProcessRecordMapper;
    private final OrderMapper orderMapper;
    private final InventoryService inventoryService;
    private final PointsService pointsService;
    private final CouponService couponService;
    private final NotificationService notificationService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processPaid(OrderPaidMessage msg) {
        // 幂等占位：重复 → 跳过（已处理过，直接 ACK）
        try {
            orderProcessRecordMapper.insert(new OrderProcessRecord(msg.getOrderNo(), "PAID"));
        } catch (DuplicateKeyException e) {
            log.info("[消费幂等] 订单{} 支付事件已处理，跳过", msg.getOrderNo());
            return;
        }

        Order order = orderMapper.selectById(msg.getOrderId());
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND); // 触发重投
        }

        // 下单已预扣库存（createOrder），支付端不再扣减，避免双扣 -2qty
        notificationService.sendOrderPaidNotify(order.getUserId(), order.getOrderNo());
        pointsService.earn(order.getUserId(), order.getFinalAmount().intValue(), order.getOrderNo(), "订单支付奖励");

        // 优惠券支付确认：FROZEN → USED（couponId = user_coupons.id 实例）
        if (msg.getCouponId() != null) {
            couponService.markUsed(msg.getCouponId(), order.getOrderNo());
        }
        log.info("[消费] 订单{} 支付事件处理完成", msg.getOrderNo());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void processCancelled(OrderCancelledMessage msg) {
        // 幂等占位：重复 → 跳过
        try {
            orderProcessRecordMapper.insert(new OrderProcessRecord(msg.getOrderNo(), "CANCELLED"));
        } catch (DuplicateKeyException e) {
            log.info("[消费幂等] 订单{} 取消事件已处理，跳过", msg.getOrderNo());
            return;
        }

        Order order = orderMapper.selectById(msg.getOrderId());
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND); // 触发重投
        }

        // 回退库存（下单预扣，取消要还；幂等靠本方法事务 + order_process_record 占位）
        inventoryService.restoreByOrder(order.getId());

        notificationService.sendOrderCancelledNotify(order.getUserId(), order.getOrderNo());

        // 解冻优惠券：FROZEN → UNUSED（幂等：已 UNUSED 视为成功）
        if (msg.getCouponId() != null) {
            couponService.unfreeze(msg.getCouponId());
        }

        // 积分回退（幂等：points_log.uk_order_type 唯一键，REFUND 只退一次）
        pointsService.refund(order.getUserId(), order.getOrderNo());
        log.info("[消费] 订单{} 取消事件处理完成", msg.getOrderNo());
    }
}
