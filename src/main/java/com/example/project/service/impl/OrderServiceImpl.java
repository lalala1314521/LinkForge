package com.example.project.service.impl;


/**
 * 订单服务实现
 * createOrder 加分布式锁，防止同一用户并发重复提交
 * payOrder/cancelOrder 完成后发布异步事件
 */

import com.example.project.common.BusinessException;
import com.example.project.common.ErrorCode;
import com.example.project.common.PageResult;
import com.example.project.dto.request.OrderCreateRequest;
import com.example.project.dto.request.OrderQueryRequest;
import com.example.project.dto.response.OrderResponse;
import com.example.project.entity.Order;
import com.example.project.enums.OrderStatus;
import com.example.project.event.OrderCancelledEvent;
import com.example.project.event.OrderPaidEvent;
import com.example.project.mapper.OrderMapper;
import com.example.project.mapper.UserMapper;
import com.example.project.service.OrderService;
import com.example.project.util.DistributedLockUtil;
import com.example.project.util.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.annotations.Select;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final SnowflakeIdGenerator idGenerator;
    private final DistributedLockUtil distributedLockUtil;
    private final ApplicationEventPublisher eventPublisher;
    private final UserMapper userMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createOrder(OrderCreateRequest request) {
        //校验下单用户是否存在
        if(userMapper.selectById(request.getUserId()) == null){
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        //同一用户5s内不允许重复提交，防止并发下单
        String lockKey = "order:create:" + request.getUserId();
        if(!distributedLockUtil.tryLock(lockKey, 500, 5, TimeUnit.SECONDS)) {
            throw new BusinessException(ErrorCode.ORDER_CREATE_BUSY);
        }
        try{
            Order order = new Order();
            order.setOrderNo(String.valueOf(idGenerator.nextId()));
            order.setUserId(request.getUserId());
            order.setTotalAmount(request.getTotalAmount());
            order.setRemark(request.getRemark());
            order.setStatus(OrderStatus.PENDING);

            orderMapper.insert(order);
            log.info("订单创建成功： order={}, userId={}", order.getOrderNo(), order.getUserId());
            return order.getId();
        }finally {
            distributedLockUtil.unlock(lockKey);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id) {
        Order order = orderMapper.selectById(id);
        if(order == null){
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        return toResponse(order);
    }
    @Override
    @Transactional(readOnly = true)
    public PageResult<OrderResponse> queryOrders(OrderQueryRequest request) {
        int offset = (request.getPage() - 1) * request.getSize();
        List<Order> order = orderMapper.selectByCondition(request, offset, request.getSize());
        long total = orderMapper.countByCondition(request);
        List<OrderResponse> responses = order.stream().map(this::toResponse).toList();
        return new PageResult<>(responses, total, request.getPage(), request.getSize());
    }
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void payOrder(Long id) {
        Order order = orderMapper.selectById(id);
        if(order == null){
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        if(order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID);
        }
        orderMapper.updateStatus(id, OrderStatus.PAID);
        log.info("订单支付成功 : id={}", id);

        //发布支付成功事件， 触发异步通知
        eventPublisher.publishEvent(new OrderPaidEvent(this, order.getId(), order.getOrderNo(),
                order.getUserId(), order.getTotalAmount()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long id) {
        Order order = orderMapper.selectById(id);
        if(order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        if(order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.SHIPPED) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID);
        }
        orderMapper.updateStatus(id, OrderStatus.CANCELLED);
        log.info("订单取消成功：id={}", id);

        eventPublisher.publishEvent(new OrderCancelledEvent(this, order.getId(), order.getOrderNo(), order.getUserId()));
    }



    private OrderResponse toResponse(Order order) {
        OrderResponse response = new OrderResponse();
        response.setId(order.getId());
        response.setOrderNo(order.getOrderNo());
        response.setUserId(order.getUserId());
        response.setTotalAmount(order.getTotalAmount());
        response.setStatus(order.getStatus());
        response.setRemark(order.getRemark());
        response.setCreatedAt(order.getCreatedAt());
        response.setUpdatedAt(order.getUpdatedAt());
        return response;
    }
}