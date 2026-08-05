package com.example.project.service;

import com.example.project.common.BusinessException;
import com.example.project.common.ErrorCode;
import com.example.project.entity.Order;
import com.example.project.entity.OrderProcessRecord;
import com.example.project.enums.OrderStatus;
import com.example.project.mapper.OrderMapper;
import com.example.project.mapper.OrderProcessRecordMapper;
import com.example.project.mq.dto.OrderCancelledMessage;
import com.example.project.mq.dto.OrderPaidMessage;
import com.example.project.service.impl.OrderEventProcessorImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 订单事件处理器测试（纯 Mockito 单测）
 * <p>
 * 库存语义（下单预扣）：processPaid 不再扣库存（verify never deduct）；
 * processCancelled 回退库存（restoreByOrder 保留，受 order_process_record 幂等保护）。
 * 覆盖：支付正常流（发积分/用券/通知）、取消正常流（回退库存/解冻券/退积分）、
 * 幂等跳过（重复消息不重复生效）、订单不存在触发重投。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("订单事件处理器测试")
class OrderEventProcessorTest {

    @Mock OrderProcessRecordMapper orderProcessRecordMapper;
    @Mock OrderMapper orderMapper;
    @Mock InventoryService inventoryService;
    @Mock PointsService pointsService;
    @Mock CouponService couponService;
    @Mock NotificationService notificationService;

    @InjectMocks OrderEventProcessorImpl processor;

    private Order buildOrder(Long id, String orderNo, Long userId, BigDecimal finalAmount) {
        Order order = new Order();
        order.setId(id);
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setCouponDiscount(BigDecimal.ZERO);
        order.setFinalAmount(finalAmount);
        order.setStatus(OrderStatus.PAID);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        return order;
    }

    private OrderPaidMessage paidMsg(Long orderId, String orderNo, Long userId, Long couponId) {
        return OrderPaidMessage.builder()
                .orderId(orderId).orderNo(orderNo).userId(userId)
                .totalAmount(new BigDecimal("100.00"))
                .couponId(couponId)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    private OrderCancelledMessage cancelMsg(Long orderId, String orderNo, Long userId, Long couponId) {
        return OrderCancelledMessage.builder()
                .orderId(orderId).orderNo(orderNo).userId(userId)
                .couponId(couponId)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    // ---- 支付正常流 ----

    @Test
    @DisplayName("processPaid - 正常流：发积分、用券、通知；下单已预扣，不再扣库存")
    void processPaid_normalFlow() {
        given(orderMapper.selectById(1L)).willReturn(buildOrder(1L, "SN1", 10L, new BigDecimal("90.00")));

        processor.processPaid(paidMsg(1L, "SN1", 10L, 5L));

        // 幂等占位写入
        verify(orderProcessRecordMapper).insert(any(OrderProcessRecord.class));
        // 下单预扣语义：支付端不再扣库存
        verify(inventoryService, never()).deduct(anyLong(), any());
        // 积分按 finalAmount 发放
        verify(pointsService).earn(10L, 90, "SN1", "订单支付奖励");
        // 优惠券支付确认
        verify(couponService).markUsed(5L, "SN1");
        // 通知
        verify(notificationService).sendOrderPaidNotify(10L, "SN1");
    }

    @Test
    @DisplayName("processPaid - 无券不调用 markUsed")
    void processPaid_withoutCoupon() {
        given(orderMapper.selectById(1L)).willReturn(buildOrder(1L, "SN1", 10L, new BigDecimal("100.00")));

        processor.processPaid(paidMsg(1L, "SN1", 10L, null));

        verify(couponService, never()).markUsed(anyLong(), anyString());
        verify(inventoryService, never()).deduct(anyLong(), any());
    }

    // ---- 幂等跳过 ----

    @Test
    @DisplayName("processPaid - 重复投递：幂等占位冲突则跳过，不重复发积分/不用券")
    void processPaid_duplicate_skip() {
        doThrow(new DuplicateKeyException("dup order_process_record"))
                .when(orderProcessRecordMapper).insert(any(OrderProcessRecord.class));

        processor.processPaid(paidMsg(1L, "SN1", 10L, 5L));

        verify(orderMapper, never()).selectById(anyLong());
        verify(pointsService, never()).earn(anyLong(), any(), anyString(), anyString());
        verify(couponService, never()).markUsed(anyLong(), anyString());
    }

    // ---- 订单不存在 ----

    @Test
    @DisplayName("processPaid - 订单不存在抛 ORDER_NOT_FOUND（触发重投）")
    void processPaid_orderNotFound() {
        given(orderMapper.selectById(1L)).willReturn(null);

        assertThatThrownBy(() -> processor.processPaid(paidMsg(1L, "SN1", 10L, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_NOT_FOUND);
    }

    // ---- 取消正常流 ----

    @Test
    @DisplayName("processCancelled - 正常流：回退库存、解冻券、退积分")
    void processCancelled_normalFlow() {
        given(orderMapper.selectById(1L)).willReturn(buildOrder(1L, "SN1", 10L, new BigDecimal("90.00")));

        processor.processCancelled(cancelMsg(1L, "SN1", 10L, 5L));

        verify(orderProcessRecordMapper).insert(any(OrderProcessRecord.class));
        // 下单预扣，取消要还：restoreByOrder 保留
        verify(inventoryService).restoreByOrder(1L);
        verify(couponService).unfreeze(5L);
        verify(pointsService).refund(10L, "SN1");
        verify(notificationService).sendOrderCancelledNotify(10L, "SN1");
    }

    // ---- 取消幂等 ----

    @Test
    @DisplayName("processCancelled - 重复投递：跳过，不回退/不解冻/不退积分")
    void processCancelled_duplicate_skip() {
        doThrow(new DuplicateKeyException("dup order_process_record"))
                .when(orderProcessRecordMapper).insert(any(OrderProcessRecord.class));

        processor.processCancelled(cancelMsg(1L, "SN1", 10L, 5L));

        verify(inventoryService, never()).restoreByOrder(anyLong());
        verify(couponService, never()).unfreeze(anyLong());
        verify(pointsService, never()).refund(anyLong(), anyString());
    }
}
