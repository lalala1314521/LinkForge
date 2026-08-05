package com.example.project.service.impl;


/**
 * 订单服务实现
 * createOrder 加分布式锁，防止同一用户并发重复提交
 * payOrder/cancelOrder 通过本地消息表（ReliableMessageService）可靠发送 Kafka 事件
 * 订单归属校验：非管理员只能操作本人订单（Service 层纵深防御）
 */

import com.example.project.common.BusinessException;
import com.example.project.common.ErrorCode;
import com.example.project.common.PageResult;
import com.example.project.dto.request.CursorPageRequest;
import com.example.project.dto.request.OrderCreateRequest;
import com.example.project.dto.request.OrderItemRequest;
import com.example.project.dto.request.OrderQueryRequest;
import com.example.project.dto.response.CursorPageResponse;
import com.example.project.dto.response.OrderResponse;
import com.example.project.entity.Order;
import com.example.project.entity.OrderItem;
import com.example.project.entity.Product;
import com.example.project.enums.OrderStatus;
import com.example.project.mapper.OrderItemMapper;
import com.example.project.mapper.OrderMapper;
import com.example.project.mapper.ProductMapper;
import com.example.project.mapper.UserMapper;
import com.example.project.mq.MqConstants;
import com.example.project.mq.dto.OrderCancelledMessage;
import com.example.project.mq.dto.OrderPaidMessage;
import com.example.project.security.SecurityUtil;
import com.example.project.service.CouponService;
import com.example.project.service.InventoryService;
import com.example.project.service.OrderService;
import com.example.project.service.ReliableMessageService;
import com.example.project.util.DistributedLockUtil;
import com.example.project.util.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;
    private final SnowflakeIdGenerator idGenerator;
    private final DistributedLockUtil distributedLockUtil;
    private final UserMapper userMapper;
    private final ProductMapper productMapper;
    private final OrderItemMapper orderItemMapper;
    private final InventoryService inventoryService;
    private final ReliableMessageService reliableMessageService;
    private final CouponService couponService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createOrder(OrderCreateRequest request) {
        //当前登录用户即下单用户，不再信任前端传入的 userId
        Long currentUserId = SecurityUtil.getCurrentUserId();
        //校验下单用户是否存在
        if (userMapper.selectById(currentUserId) == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "商品明细不能为空");
        }
        //同一用户5s内不允许重复提交，防止并发下单
        String lockKey = "order:create:" + currentUserId;
        if (!distributedLockUtil.tryLock(lockKey, 500, 5, TimeUnit.SECONDS)) {
            throw new BusinessException(ErrorCode.ORDER_CREATE_BUSY);
        }
        try {
            //同商品合并数量，防止同一商品重复提交导致重复扣减
            Map<Long, Integer> merged = mergeItems(request.getItems());
            BigDecimal totalAmount = BigDecimal.ZERO;
            List<OrderItem> items = new ArrayList<>();

            // 服务端计价 + 下单预扣库存：价格取数据库快照；条件扣减（WHERE stock>=qty）防超卖
            for (Map.Entry<Long, Integer> entry : merged.entrySet()) {
                Long productId = entry.getKey();
                Integer quantity = entry.getValue();
                Product product = productMapper.selectById(productId);
                if (product == null || !"ON_SALE".equals(product.getStatus())) {
                    throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
                }
                // 下单预扣库存：影响行数=0 → 库存不足，整单回滚（与批次 0 一致）
                if (!inventoryService.deduct(productId, quantity)) {
                    throw new BusinessException(ErrorCode.STOCK_INSUFFICIENT);
                }
                BigDecimal lineAmount = product.getPrice().multiply(BigDecimal.valueOf(quantity));
                totalAmount = totalAmount.add(lineAmount);

                OrderItem item = new OrderItem();
                item.setProductId(productId);
                item.setQuantity(quantity);
                item.setPrice(product.getPrice());
                items.add(item);
            }

            // 订单号先于冻结生成（写入 user_coupons.order_no）
            String orderNo = String.valueOf(idGenerator.nextId());

            // 优惠券冻结+抵扣：couponId 为空时行为与批次 0 完全一致（discount=0, final=total）
            BigDecimal couponDiscount = BigDecimal.ZERO;
            BigDecimal finalAmount = totalAmount;
            Long userCouponId = null;
            if (request.getCouponId() != null) {
                couponDiscount = couponService.freezeForOrder(request.getCouponId(), currentUserId, orderNo, totalAmount);
                finalAmount = totalAmount.subtract(couponDiscount).max(BigDecimal.ZERO);
                userCouponId = request.getCouponId();   // order.coupon_id = user_coupons.id（实例）
            }

            Order order = new Order();
            order.setOrderNo(orderNo);
            order.setUserId(currentUserId);
            order.setTotalAmount(totalAmount);
            order.setCouponId(userCouponId);
            order.setCouponDiscount(couponDiscount);
            order.setFinalAmount(finalAmount);
            order.setRemark(request.getRemark());
            order.setStatus(OrderStatus.PENDING);

            // 冻结与插单同事务：任一步失败整体回滚（FROZEN 自动还原 UNUSED）
            orderMapper.insert(order);

            for (OrderItem item : items) {
                item.setOrderId(order.getId());
                orderItemMapper.insert(item);
            }
            log.info("订单创建成功： orderNo={}, userId={}, totalAmount={}, couponDiscount={}, finalAmount={}",
                    order.getOrderNo(), currentUserId, totalAmount, couponDiscount, finalAmount);
            return order.getId();
        } finally {
            distributedLockUtil.unlock(lockKey);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id) {
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        checkOrderOwner(order);
        return toResponse(order);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<OrderResponse> queryOrders(OrderQueryRequest request) {
        //非管理员强制查本人订单，不再信任前端传入的 userId 过滤条件
        if (!SecurityUtil.isAdmin()) {
            request.setUserId(SecurityUtil.getCurrentUserId());
        }
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
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        checkOrderOwner(order);
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID);
        }
        orderMapper.updateStatus(id, OrderStatus.PAID);
        log.info("订单支付成功 : id={}", id);

        // 可靠消息：事务内写 PENDING，事务提交后才真正发 Kafka（避免消息丢失）
        OrderPaidMessage msg = OrderPaidMessage.builder()
                .orderId(order.getId())
                .orderNo(order.getOrderNo())
                .userId(order.getUserId())
                .totalAmount(order.getTotalAmount())
                .couponId(order.getCouponId())
                .timestamp(System.currentTimeMillis())
                .build();
        String messageKey = String.valueOf(order.getUserId());
        Long msgId = reliableMessageService.savePendingMessage(MqConstants.TOPIC_ORDER_PAID, messageKey, msg);
        reliableMessageService.sendAfterCommit(msgId, MqConstants.TOPIC_ORDER_PAID, messageKey, msg);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long id) {
        Order order = orderMapper.selectById(id);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        checkOrderOwner(order);
        if (order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.SHIPPED) {
            throw new BusinessException(ErrorCode.ORDER_STATUS_INVALID);
        }
        orderMapper.updateStatus(id, OrderStatus.CANCELLED);
        log.info("订单取消成功：id={}", id);

        // 可靠消息：事务内写 PENDING，事务提交后才真正发 Kafka
        OrderCancelledMessage msg = OrderCancelledMessage.builder()
                .orderId(order.getId())
                .orderNo(order.getOrderNo())
                .userId(order.getUserId())
                .couponId(order.getCouponId())
                .timestamp(System.currentTimeMillis())
                .build();
        String messageKey = String.valueOf(order.getUserId());
        Long msgId = reliableMessageService.savePendingMessage(MqConstants.TOPIC_ORDER_CANCELLED, messageKey, msg);
        reliableMessageService.sendAfterCommit(msgId, MqConstants.TOPIC_ORDER_CANCELLED, messageKey, msg);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrderByTimeout(Long id) {
        Order order = orderMapper.selectById(id);
        if (order == null) {
            log.warn("[超时关单] 订单不存在：id={}", id);
            return;
        }
        // 条件更新 PENDING→CANCELLED：影响行数 0 → 已被用户取消/已支付/并发处理，跳过（幂等）
        if (orderMapper.updateStatusIfPending(id, OrderStatus.CANCELLED) == 0) {
            log.info("[超时关单] 订单{} 状态非 PENDING，跳过", id);
            return;
        }
        log.info("[超时关单] 订单自动取消成功：id={}", id);

        // 消息链路复用（与 cancelOrder 尾部一致）：消费端 processCancelled 回退库存/解冻券/退积分
        OrderCancelledMessage msg = OrderCancelledMessage.builder()
                .orderId(order.getId())
                .orderNo(order.getOrderNo())
                .userId(order.getUserId())
                .couponId(order.getCouponId())
                .timestamp(System.currentTimeMillis())
                .build();
        String messageKey = String.valueOf(order.getUserId());
        Long msgId = reliableMessageService.savePendingMessage(MqConstants.TOPIC_ORDER_CANCELLED, messageKey, msg);
        reliableMessageService.sendAfterCommit(msgId, MqConstants.TOPIC_ORDER_CANCELLED, messageKey, msg);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<OrderResponse> queryOrdersByCursor(CursorPageRequest request) {
        //管理员可查全部订单，非管理员强制查本人订单
        Long userId = SecurityUtil.isAdmin() ? null : SecurityUtil.getCurrentUserId();
        int fetchSize = request.getSize() + 1;
        List<Order> orders = orderMapper.selectByCursor(request.getLastId(), userId, fetchSize);

        boolean hasMore = orders.size() > request.getSize();
        if (hasMore) {
            orders = orders.subList(0, request.getSize());
        }

        Long nextLastId = null;
        if (!orders.isEmpty()) {
            nextLastId = orders.get(orders.size() - 1).getId();
        }

        List<OrderResponse> responses = orders.stream().map(this::toResponse).toList();
        return new CursorPageResponse<>(responses, nextLastId, hasMore);
    }

    /**
     * 订单归属校验：非管理员只能操作本人订单
     */
    private void checkOrderOwner(Order order) {
        if (!SecurityUtil.isAdmin() && !order.getUserId().equals(SecurityUtil.getCurrentUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    /**
     * 合并同商品数量，防止同一商品重复提交导致重复扣减
     */
    private Map<Long, Integer> mergeItems(List<OrderItemRequest> items) {
        Map<Long, Integer> merged = new LinkedHashMap<>();
        for (OrderItemRequest item : items) {
            merged.merge(item.getProductId(), item.getQuantity(), Integer::sum);
        }
        return merged;
    }

    private OrderResponse toResponse(Order order) {
        OrderResponse response = new OrderResponse();
        response.setId(order.getId());
        response.setOrderNo(order.getOrderNo());
        response.setUserId(order.getUserId());
        response.setTotalAmount(order.getTotalAmount());
        response.setCouponId(order.getCouponId());
        response.setCouponDiscount(order.getCouponDiscount());
        response.setFinalAmount(order.getFinalAmount());
        response.setStatus(order.getStatus());
        response.setRemark(order.getRemark());
        response.setCreatedAt(order.getCreatedAt());
        response.setUpdatedAt(order.getUpdatedAt());
        return response;
    }
}
