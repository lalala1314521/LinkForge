package com.example.project.service;

import com.example.project.dto.request.CartAddRequest;
import com.example.project.dto.response.CartItemResponse;

import java.util.List;

/**
 * 购物车服务
 */
public interface CartService {

    /** 加入购物车（已存在则累加数量） */
    void addCart(Long userId, CartAddRequest request);

    /** 修改数量（1-999） */
    void updateQuantity(Long userId, Long cartId, Integer quantity);

    /** 删除条目 */
    void removeItem(Long userId, Long cartId);

    /** 购物车列表（含商品信息） */
    List<CartItemResponse> listCart(Long userId);

    /** 结算后清空购物车 */
    void clearCart(Long userId);

    /** 购物车商品总数（导航角标） */
    int countCart(Long userId);
}
