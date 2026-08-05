package com.example.project.service;

import com.example.project.common.BusinessException;
import com.example.project.common.ErrorCode;
import com.example.project.entity.SeckillActivity;
import com.example.project.entity.SeckillOrder;
import com.example.project.mapper.SeckillActivityMapper;
import com.example.project.mapper.SeckillOrderMapper;
import com.example.project.mq.dto.SeckillOrderMessage;
import com.example.project.service.impl.SeckillOrderProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 秒杀消费处理器测试（纯 Mockito 单测）
 * <p>
 * 覆盖：正常落单（PENDING + 真实秒杀价 + DB 扣减）、重复消息幂等跳过、
 * 活动不存在（触发重投）、DB 异常传播（不 ACK 由 Consumer 处理）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("秒杀消费处理器测试")
class SeckillConsumerTest {

    @Mock SeckillOrderMapper seckillOrderMapper;
    @Mock SeckillActivityMapper seckillActivityMapper;

    @InjectMocks SeckillOrderProcessor processor;

    private SeckillActivity buildActivity(Long id) {
        SeckillActivity a = new SeckillActivity();
        a.setId(id);
        a.setName("测试秒杀");
        a.setProductId(100L);
        a.setSeckillPrice(new BigDecimal("9.90"));
        a.setTotalStock(100);
        a.setAvailableStock(80);
        a.setStatus("ACTIVE");
        return a;
    }

    private SeckillOrderMessage msg(String orderNo) {
        return SeckillOrderMessage.builder()
                .orderNo(orderNo)
                .userId(1L)
                .activityId(1L)
                .productId(100L)
                .price(new BigDecimal("9.90"))
                .timestamp(System.currentTimeMillis())
                .build();
    }

    @Test
    @DisplayName("process - 正常流：落 PENDING 单 + DB 库存条件扣减")
    void process_normal() {
        given(seckillActivityMapper.selectById(1L)).willReturn(buildActivity(1L));
        given(seckillActivityMapper.decrementAvailableStock(1L)).willReturn(1);

        processor.process(msg("SK100"));

        org.mockito.ArgumentCaptor<SeckillOrder> captor = org.mockito.ArgumentCaptor.forClass(SeckillOrder.class);
        verify(seckillOrderMapper).insert(captor.capture());
        // 落 PENDING（D3）+ 真实秒杀价（D2）
        org.assertj.core.api.BDDAssertions.then(captor.getValue().getStatus()).isEqualTo("PENDING");
        org.assertj.core.api.BDDAssertions.then(captor.getValue().getPrice()).isEqualByComparingTo("9.90");
        verify(seckillActivityMapper).decrementAvailableStock(1L);
    }

    @Test
    @DisplayName("process - 重复消息：唯一键冲突跳过，不重复扣 DB 库存")
    void process_duplicate_skip() {
        given(seckillActivityMapper.selectById(1L)).willReturn(buildActivity(1L));
        doThrow(new DuplicateKeyException("dup seckill_orders idx_order_no"))
                .when(seckillOrderMapper).insert(any(SeckillOrder.class));

        processor.process(msg("SK100"));

        verify(seckillActivityMapper, never()).decrementAvailableStock(anyLong());
    }

    @Test
    @DisplayName("process - 活动不存在：抛异常触发重投，不落单")
    void process_activityNotFound() {
        given(seckillActivityMapper.selectById(1L)).willReturn(null);

        assertThatThrownBy(() -> processor.process(msg("SK100")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SECKILL_ACTIVITY_NOT_FOUND);
        verify(seckillOrderMapper, never()).insert(any());
        verify(seckillActivityMapper, never()).decrementAvailableStock(anyLong());
    }

    @Test
    @DisplayName("process - DB 异常：异常传播（Consumer 不 ACK → 重投）")
    void process_dbException_propagates() {
        given(seckillActivityMapper.selectById(1L)).willReturn(buildActivity(1L));
        doThrow(new RuntimeException("db down")).when(seckillOrderMapper).insert(any(SeckillOrder.class));

        assertThatThrownBy(() -> processor.process(msg("SK100")))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("db down");
        verify(seckillActivityMapper, never()).decrementAvailableStock(anyLong());
    }
}
