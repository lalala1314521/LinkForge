package com.example.project.service.impl;

import com.example.project.entity.OrderItem;
import com.example.project.mapper.OrderItemMapper;
import com.example.project.mapper.ProductMapper;
import com.example.project.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 库存服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final ProductMapper productMapper;
    private final OrderItemMapper orderItemMapper;

    @Override
    public boolean deduct(Long productId, Integer quantity) {
        int affected = productMapper.decrementStock(productId, quantity);
        if (affected == 1) {
            log.info("[库存] 扣减成功：productId={}, quantity={}", productId, quantity);
        } else {
            log.warn("[库存] 扣减失败（库存不足）：productId={}, quantity={}", productId, quantity);
        }
        return affected == 1;
    }

    @Override
    public void restoreByOrder(Long orderId) {
        for (OrderItem item : orderItemMapper.selectByOrderId(orderId)) {
            int affected = productMapper.incrementStock(item.getProductId(), item.getQuantity());
            log.info("[库存] 回退：orderId={}, productId={}, quantity={}, affected={}",
                    orderId, item.getProductId(), item.getQuantity(), affected);
        }
    }
}
