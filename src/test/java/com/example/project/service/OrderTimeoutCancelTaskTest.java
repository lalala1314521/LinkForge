package com.example.project.service;

import com.example.project.entity.Order;
import com.example.project.enums.OrderStatus;
import com.example.project.mapper.OrderMapper;
import com.example.project.util.DistributedLockUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 订单超时关单任务测试（批次 4 T1）
 * <p>
 * 覆盖：正常扫描逐单关单（+锁获取/释放）、未抢到分布式锁跳过扫描。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("订单超时关单任务测试")
class OrderTimeoutCancelTaskTest {

    @Mock OrderMapper orderMapper;
    @Mock OrderService orderService;
    @Mock DistributedLockUtil distributedLockUtil;

    @InjectMocks OrderTimeoutCancelTask task;

    @BeforeEach
    void setUp() {
        // @Value 字段在纯单测下默认 0，反射注入
        ReflectionTestUtils.setField(task, "timeoutMinutes", 15L);
    }

    private Order buildPendingOrder(Long id) {
        Order order = new Order();
        order.setId(id);
        order.setOrderNo("SN" + id);
        order.setUserId(1L);
        order.setStatus(OrderStatus.PENDING);
        order.setCreatedAt(LocalDateTime.now().minusHours(1));
        return order;
    }

    @Test
    @DisplayName("cancelTimeoutOrders - 扫描超时 PENDING 订单并逐单关单")
    void cancelTimeoutOrders_normal() {
        given(distributedLockUtil.tryLock("order:timeout-cancel", 0, 60, TimeUnit.SECONDS))
                .willReturn(true);
        given(orderMapper.selectTimeoutPending(any(LocalDateTime.class)))
                .willReturn(List.of(buildPendingOrder(1L), buildPendingOrder(2L)));

        task.cancelTimeoutOrders();

        verify(orderService).cancelOrderByTimeout(1L);
        verify(orderService).cancelOrderByTimeout(2L);
        verify(distributedLockUtil).unlock("order:timeout-cancel");
    }

    @Test
    @DisplayName("cancelTimeoutOrders - 未抢到分布式锁跳过扫描")
    void cancelTimeoutOrders_lockNotAcquired() {
        given(distributedLockUtil.tryLock("order:timeout-cancel", 0, 60, TimeUnit.SECONDS))
                .willReturn(false);

        task.cancelTimeoutOrders();

        verify(orderMapper, never()).selectTimeoutPending(any(LocalDateTime.class));
        verify(orderService, never()).cancelOrderByTimeout(any());
    }
}
