package com.example.project.service;


import com.example.project.common.PageResult;
import com.example.project.dto.request.CursorPageRequest;
import com.example.project.dto.request.OrderCreateRequest;
import com.example.project.dto.request.OrderQueryRequest;
import com.example.project.dto.response.CursorPageResponse;
import com.example.project.dto.response.OrderResponse;

/**
 * 订单服务接口
 */
public interface OrderService {

    Long createOrder(OrderCreateRequest request);

    OrderResponse getOrderById(Long id);

    PageResult<OrderResponse> queryOrders(OrderQueryRequest request);

    CursorPageResponse<OrderResponse> queryOrdersByCursor(CursorPageRequest request);

    void payOrder(Long id);

    void cancelOrder(Long id);

    /**
     * 超时关单（定时任务用）：条件更新 PENDING→CANCELLED（影响行数 0 跳过，幂等）→ 走本地消息表发送取消消息。
     * 与 cancelOrder 区别：不校验 SecurityContext 归属（系统任务），条件更新防并发重复处理。
     */
    void cancelOrderByTimeout(Long id);
}