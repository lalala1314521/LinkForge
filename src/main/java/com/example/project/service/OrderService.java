package com.example.project.service;


import com.example.project.common.PageResult;
import com.example.project.dto.request.OrderCreateRequest;
import com.example.project.dto.request.OrderQueryRequest;
import com.example.project.dto.response.OrderResponse;

/**
 * 订单服务接口
 */
public interface OrderService {

    Long createOrder(OrderCreateRequest request);

    OrderResponse getOrderById(Long id);

    PageResult<OrderResponse> queryOrders(OrderQueryRequest request);

    void payOrder(Long id);

    void cancelOrder(Long id);
}