package com.example.project.service.impl;

import com.example.project.common.BusinessException;
import com.example.project.common.ErrorCode;
import com.example.project.dto.request.CartAddRequest;
import com.example.project.dto.response.CartItemResponse;
import com.example.project.entity.Product;
import com.example.project.mapper.CartMapper;
import com.example.project.mapper.ProductMapper;
import com.example.project.service.CartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 购物车服务实现
 * <p>
 * 商品存在校验（PRODUCT_NOT_FOUND）；数量 1-999；
 * 加购重复商品由 uk_user_product 唯一键 + ON DUPLICATE KEY 累加。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CartServiceImpl implements CartService {

    private static final int MAX_QUANTITY = 999;

    private final CartMapper cartMapper;
    private final ProductMapper productMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addCart(Long userId, CartAddRequest request) {
        Product product = productMapper.selectById(request.getProductId());
        if (product == null) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        cartMapper.upsert(userId, request.getProductId(), request.getQuantity());
        log.info("[购物车] 用户{} 加购商品{} x{}", userId, request.getProductId(), request.getQuantity());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateQuantity(Long userId, Long cartId, Integer quantity) {
        if (quantity == null || quantity < 1 || quantity > MAX_QUANTITY) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        int rows = cartMapper.updateQuantity(cartId, userId, quantity);
        if (rows == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeItem(Long userId, Long cartId) {
        cartMapper.deleteById(cartId, userId);
    }

    @Override
    public List<CartItemResponse> listCart(Long userId) {
        return cartMapper.selectByUserId(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearCart(Long userId) {
        cartMapper.clearByUserId(userId);
    }

    @Override
    public int countCart(Long userId) {
        return cartMapper.countByUserId(userId);
    }
}
