package com.example.project.service;

import com.example.project.entity.OrderItem;
import com.example.project.mapper.OrderItemMapper;
import com.example.project.mapper.ProductMapper;
import com.example.project.service.impl.InventoryServiceImpl;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verify;

/**
 * 库存服务测试（批次 1）
 * <p>
 * 覆盖：deduct 条件扣减（正常/库存不足返回 false）、restoreByOrder 按订单明细逐条回退。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("库存服务测试")
class InventoryServiceTest {

    @Mock ProductMapper productMapper;
    @Mock OrderItemMapper orderItemMapper;

    @InjectMocks InventoryServiceImpl inventoryService;

    private OrderItem buildItem(Long orderId, Long productId, int quantity) {
        OrderItem item = new OrderItem();
        item.setOrderId(orderId);
        item.setProductId(productId);
        item.setQuantity(quantity);
        item.setPrice(new BigDecimal("10.00"));
        return item;
    }

    @Test
    @DisplayName("deduct - 扣减成功（WHERE stock>=qty 影响行数=1）返回 true")
    void deduct_success() {
        given(productMapper.decrementStock(1L, 2)).willReturn(1);

        boolean ok = inventoryService.deduct(1L, 2);

        BDDAssertions.then(ok).isTrue();
        verify(productMapper).decrementStock(1L, 2);
    }

    @Test
    @DisplayName("deduct - 库存不足（影响行数=0）返回 false")
    void deduct_insufficient() {
        given(productMapper.decrementStock(1L, 99)).willReturn(0);

        boolean ok = inventoryService.deduct(1L, 99);

        BDDAssertions.then(ok).isFalse();
    }

    @Test
    @DisplayName("restoreByOrder - 按 order_items 明细逐条回加库存")
    void restoreByOrder_restoresEachItem() {
        given(orderItemMapper.selectByOrderId(1L)).willReturn(List.of(
                buildItem(1L, 100L, 2),
                buildItem(1L, 200L, 3)
        ));

        inventoryService.restoreByOrder(1L);

        verify(productMapper).incrementStock(100L, 2);
        verify(productMapper).incrementStock(200L, 3);
    }

    @Test
    @DisplayName("restoreByOrder - 无明细不调用 incrementStock")
    void restoreByOrder_empty() {
        given(orderItemMapper.selectByOrderId(1L)).willReturn(List.of());

        inventoryService.restoreByOrder(1L);

        then(productMapper).should(org.mockito.Mockito.never()).incrementStock(anyLong(), anyInt());
    }
}
