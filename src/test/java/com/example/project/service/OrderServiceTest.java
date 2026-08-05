package com.example.project.service;

import com.example.project.common.BusinessException;
import com.example.project.common.ErrorCode;
import com.example.project.dto.request.CursorPageRequest;
import com.example.project.dto.request.OrderCreateRequest;
import com.example.project.dto.request.OrderItemRequest;
import com.example.project.dto.response.CursorPageResponse;
import com.example.project.dto.response.OrderResponse;
import com.example.project.entity.Order;
import com.example.project.entity.OrderItem;
import com.example.project.entity.Product;
import com.example.project.entity.User;
import com.example.project.enums.OrderStatus;
import com.example.project.enums.UserRole;
import com.example.project.enums.UserStatus;
import com.example.project.mapper.OrderItemMapper;
import com.example.project.mapper.OrderMapper;
import com.example.project.mapper.ProductMapper;
import com.example.project.mapper.UserMapper;
import com.example.project.mq.MqConstants;
import com.example.project.mq.dto.OrderCancelledMessage;
import com.example.project.mq.dto.OrderPaidMessage;
import com.example.project.service.impl.OrderServiceImpl;
import com.example.project.util.DistributedLockUtil;
import com.example.project.util.SnowflakeIdGenerator;
import org.assertj.core.api.BDDAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

/**
 * 订单服务完整测试（批次 0/1 改造后）
 * <p>
 * 覆盖：createOrder 用券冻结+抵扣/空券行为/冻结失败传播/库存不足/商品不存在/锁失败；
 * payOrder/cancelOrder 消息链路（savePendingMessage + sendAfterCommit）与异常分支；归属校验；游标分页 hasMore。
 * （注：createOrder 计价/合并、归属校验另有 CreateOrderServiceTest/OrderSecurityTest 覆盖，本类聚焦批次1新增路径）
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("订单服务测试")
class OrderServiceTest {

    @Mock OrderMapper orderMapper;
    @Mock UserMapper userMapper;
    @Mock ProductMapper productMapper;
    @Mock OrderItemMapper orderItemMapper;
    @Mock SnowflakeIdGenerator idGenerator;
    @Mock DistributedLockUtil distributedLockUtil;
    @Mock InventoryService inventoryService;
    @Mock ReliableMessageService reliableMessageService;
    @Mock CouponService couponService;

    @InjectMocks OrderServiceImpl orderService;

    private static final Long CURRENT_USER = 1L;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        // 默认登录用户 1（ROLE_USER）；归属用例覆盖为其他用户
        loginAs(CURRENT_USER, "USER");
        lenient().when(distributedLockUtil.tryLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void loginAs(Long userId, String role) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "user" + userId, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        auth.setDetails(userId);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private User buildUser(Long id) {
        User u = new User();
        u.setId(id);
        u.setUsername("user" + id);
        u.setPassword("encoded");
        u.setRole(UserRole.USER);
        u.setStatus(UserStatus.ACTIVE);
        u.setCreatedAt(LocalDateTime.now());
        u.setUpdatedAt(LocalDateTime.now());
        return u;
    }

    private Product buildProduct(Long id, BigDecimal price, String status) {
        Product p = new Product();
        p.setId(id);
        p.setName("商品" + id);
        p.setPrice(price);
        p.setStock(100);
        p.setStatus(status);
        return p;
    }

    private Order buildOrder(Long id, Long ownerId, OrderStatus status, Long couponId) {
        Order order = new Order();
        order.setId(id);
        order.setOrderNo("SN" + id);
        order.setUserId(ownerId);
        order.setTotalAmount(new BigDecimal("99.00"));
        order.setCouponId(couponId);
        order.setCouponDiscount(BigDecimal.ZERO);
        order.setFinalAmount(new BigDecimal("99.00"));
        order.setStatus(status);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        return order;
    }

    private OrderItemRequest item(Long productId, int quantity) {
        OrderItemRequest req = new OrderItemRequest();
        req.setProductId(productId);
        req.setQuantity(quantity);
        return req;
    }

    private OrderCreateRequest request(Long couponId, OrderItemRequest... items) {
        OrderCreateRequest req = new OrderCreateRequest();
        req.setItems(List.of(items));
        req.setCouponId(couponId);
        return req;
    }

    private ArgumentCaptor<Order> captureOrderAndSetId(Long id) {
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        org.mockito.Mockito.doAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(id);
            return null;
        }).when(orderMapper).insert(captor.capture());
        return captor;
    }

    // ---- createOrder：优惠券冻结+抵扣 ----

    @Test
    @DisplayName("createOrder - 用券：冻结+抵扣，couponDiscount/finalAmount/couponId 正确")
    void createOrder_withCoupon() {
        given(userMapper.selectById(CURRENT_USER)).willReturn(buildUser(CURRENT_USER));
        given(productMapper.selectById(1L)).willReturn(buildProduct(1L, new BigDecimal("10.00"), "ON_SALE"));
        given(inventoryService.deduct(1L, 2)).willReturn(true);   // 下单预扣库存
        given(idGenerator.nextId()).willReturn(9001L);
        // 券抵扣 10 元：总价 20.00 → final 10.00
        given(couponService.freezeForOrder(eq(5L), eq(CURRENT_USER), eq("9001"), eq(new BigDecimal("20.00"))))
                .willReturn(new BigDecimal("10.00"));
        ArgumentCaptor<Order> orderCaptor = captureOrderAndSetId(100L);

        Long orderId = orderService.createOrder(request(5L, item(1L, 2)));

        BDDAssertions.then(orderId).isEqualTo(100L);
        // 冻结入参：userCouponId=5、orderNo=9001、orderTotal=20.00
        then(couponService).should().freezeForOrder(5L, CURRENT_USER, "9001", new BigDecimal("20.00"));
        Order saved = orderCaptor.getValue();
        BDDAssertions.then(saved.getCouponId()).isEqualTo(5L);
        BDDAssertions.then(saved.getCouponDiscount()).isEqualByComparingTo("10.00");
        BDDAssertions.then(saved.getFinalAmount()).isEqualByComparingTo("10.00");
    }

    @Test
    @DisplayName("createOrder - 空券：不冻结，couponDiscount=0，final=total（批次0行为）")
    void createOrder_withoutCoupon() {
        given(userMapper.selectById(CURRENT_USER)).willReturn(buildUser(CURRENT_USER));
        given(productMapper.selectById(1L)).willReturn(buildProduct(1L, new BigDecimal("10.00"), "ON_SALE"));
        given(inventoryService.deduct(1L, 2)).willReturn(true);
        given(idGenerator.nextId()).willReturn(9001L);
        ArgumentCaptor<Order> orderCaptor = captureOrderAndSetId(100L);

        orderService.createOrder(request(null, item(1L, 2)));

        then(couponService).should(never()).freezeForOrder(anyLong(), anyLong(), anyString(), any());
        Order saved = orderCaptor.getValue();
        BDDAssertions.then(saved.getCouponId()).isNull();
        BDDAssertions.then(saved.getCouponDiscount()).isEqualByComparingTo("0");
        BDDAssertions.then(saved.getFinalAmount()).isEqualByComparingTo("20.00");
    }

    @Test
    @DisplayName("createOrder - 券冻结失败：异常传播，不落单")
    void createOrder_couponFreezeFailure() {
        given(userMapper.selectById(CURRENT_USER)).willReturn(buildUser(CURRENT_USER));
        given(productMapper.selectById(1L)).willReturn(buildProduct(1L, new BigDecimal("10.00"), "ON_SALE"));
        given(inventoryService.deduct(1L, 2)).willReturn(true);
        given(idGenerator.nextId()).willReturn(9001L);
        given(couponService.freezeForOrder(anyLong(), anyLong(), anyString(), any()))
                .willThrow(new BusinessException(ErrorCode.COUPON_ALREADY_USED));

        assertThatThrownBy(() -> orderService.createOrder(request(5L, item(1L, 2))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.COUPON_ALREADY_USED);
        then(orderMapper).should(never()).insert(any(Order.class));
        then(orderItemMapper).should(never()).insert(any(OrderItem.class));
    }

    // ---- createOrder：库存/商品/锁 ----

    @Test
    @DisplayName("createOrder - 库存不足抛 STOCK_INSUFFICIENT，不落单")
    void createOrder_insufficientStock() {
        given(userMapper.selectById(CURRENT_USER)).willReturn(buildUser(CURRENT_USER));
        given(productMapper.selectById(1L)).willReturn(buildProduct(1L, new BigDecimal("10.00"), "ON_SALE"));
        given(inventoryService.deduct(1L, 2)).willReturn(false);

        assertThatThrownBy(() -> orderService.createOrder(request(null, item(1L, 2))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.STOCK_INSUFFICIENT);
        then(orderMapper).should(never()).insert(any(Order.class));
    }

    @Test
    @DisplayName("createOrder - 商品不存在/下架抛 PRODUCT_NOT_FOUND")
    void createOrder_productNotFound() {
        given(userMapper.selectById(CURRENT_USER)).willReturn(buildUser(CURRENT_USER));
        given(productMapper.selectById(99L)).willReturn(null);

        assertThatThrownBy(() -> orderService.createOrder(request(null, item(99L, 1))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
        then(orderMapper).should(never()).insert(any(Order.class));
    }

    @Test
    @DisplayName("createOrder - 分布式锁失败抛 ORDER_CREATE_BUSY")
    void createOrder_lockBusy() {
        given(userMapper.selectById(CURRENT_USER)).willReturn(buildUser(CURRENT_USER));
        given(distributedLockUtil.tryLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class)))
                .willReturn(false);

        assertThatThrownBy(() -> orderService.createOrder(request(null, item(1L, 1))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_CREATE_BUSY);
        then(orderMapper).should(never()).insert(any(Order.class));
    }

    // ---- payOrder ----

    @Test
    @DisplayName("payOrder - 正常：PENDING→PAID + savePendingMessage + sendAfterCommit")
    void payOrder_success() {
        loginAs(CURRENT_USER, "USER");
        Order order = buildOrder(1L, CURRENT_USER, OrderStatus.PENDING, 5L);
        given(orderMapper.selectById(1L)).willReturn(order);
        given(reliableMessageService.savePendingMessage(eq(MqConstants.TOPIC_ORDER_PAID), eq("1"), any(OrderPaidMessage.class)))
                .willReturn(77L);

        orderService.payOrder(1L);

        then(orderMapper).should().updateStatus(1L, OrderStatus.PAID);
        then(reliableMessageService).should().savePendingMessage(eq(MqConstants.TOPIC_ORDER_PAID), eq("1"), any(OrderPaidMessage.class));
        then(reliableMessageService).should().sendAfterCommit(eq(77L), eq(MqConstants.TOPIC_ORDER_PAID), eq("1"), any(OrderPaidMessage.class));
        // 消息携带订单 couponId（user_coupons.id 实例）
        ArgumentCaptor<OrderPaidMessage> captor = ArgumentCaptor.forClass(OrderPaidMessage.class);
        org.mockito.Mockito.verify(reliableMessageService).savePendingMessage(eq(MqConstants.TOPIC_ORDER_PAID), eq("1"), captor.capture());
        BDDAssertions.then(captor.getValue().getCouponId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("payOrder - 订单不存在抛 ORDER_NOT_FOUND")
    void payOrder_orderNotFound() {
        given(orderMapper.selectById(1L)).willReturn(null);

        assertThatThrownBy(() -> orderService.payOrder(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_NOT_FOUND);
        then(orderMapper).should(never()).updateStatus(anyLong(), any(OrderStatus.class));
        then(reliableMessageService).should(never()).savePendingMessage(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("payOrder - 状态非 PENDING 抛 ORDER_STATUS_INVALID")
    void payOrder_statusInvalid() {
        loginAs(CURRENT_USER, "USER");
        given(orderMapper.selectById(1L)).willReturn(buildOrder(1L, CURRENT_USER, OrderStatus.PAID, null));

        assertThatThrownBy(() -> orderService.payOrder(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_STATUS_INVALID);
        then(orderMapper).should(never()).updateStatus(anyLong(), any(OrderStatus.class));
    }

    @Test
    @DisplayName("payOrder - 非本人且非 ADMIN 抛 FORBIDDEN")
    void payOrder_forbidden() {
        loginAs(2L, "USER");
        given(orderMapper.selectById(1L)).willReturn(buildOrder(1L, 1L, OrderStatus.PENDING, null));

        assertThatThrownBy(() -> orderService.payOrder(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
        then(orderMapper).should(never()).updateStatus(anyLong(), any(OrderStatus.class));
    }

    @Test
    @DisplayName("payOrder - ADMIN 可支付他人订单")
    void payOrder_adminExempt() {
        loginAs(1L, "ADMIN");
        given(orderMapper.selectById(1L)).willReturn(buildOrder(1L, 2L, OrderStatus.PENDING, null));
        given(reliableMessageService.savePendingMessage(anyString(), anyString(), any())).willReturn(77L);

        orderService.payOrder(1L);

        then(orderMapper).should().updateStatus(1L, OrderStatus.PAID);
    }

    // ---- cancelOrder ----

    @Test
    @DisplayName("cancelOrder - 正常：状态改 CANCELLED + 消息链路携带 couponId")
    void cancelOrder_success() {
        loginAs(CURRENT_USER, "USER");
        Order order = buildOrder(1L, CURRENT_USER, OrderStatus.PENDING, 5L);
        given(orderMapper.selectById(1L)).willReturn(order);
        given(reliableMessageService.savePendingMessage(eq(MqConstants.TOPIC_ORDER_CANCELLED), eq("1"), any(OrderCancelledMessage.class)))
                .willReturn(78L);

        orderService.cancelOrder(1L);

        then(orderMapper).should().updateStatus(1L, OrderStatus.CANCELLED);
        then(reliableMessageService).should().sendAfterCommit(eq(78L), eq(MqConstants.TOPIC_ORDER_CANCELLED), eq("1"), any(OrderCancelledMessage.class));
        ArgumentCaptor<OrderCancelledMessage> captor = ArgumentCaptor.forClass(OrderCancelledMessage.class);
        org.mockito.Mockito.verify(reliableMessageService).savePendingMessage(eq(MqConstants.TOPIC_ORDER_CANCELLED), eq("1"), captor.capture());
        BDDAssertions.then(captor.getValue().getCouponId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("cancelOrder - 订单不存在抛 ORDER_NOT_FOUND")
    void cancelOrder_orderNotFound() {
        given(orderMapper.selectById(1L)).willReturn(null);

        assertThatThrownBy(() -> orderService.cancelOrder(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_NOT_FOUND);
        then(reliableMessageService).should(never()).savePendingMessage(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("cancelOrder - COMPLETED 不可取消抛 ORDER_STATUS_INVALID")
    void cancelOrder_completedInvalid() {
        loginAs(CURRENT_USER, "USER");
        given(orderMapper.selectById(1L)).willReturn(buildOrder(1L, CURRENT_USER, OrderStatus.COMPLETED, null));

        assertThatThrownBy(() -> orderService.cancelOrder(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_STATUS_INVALID);
        then(orderMapper).should(never()).updateStatus(anyLong(), any(OrderStatus.class));
    }

    // ---- queryOrdersByCursor：hasMore 边界 ----

    @Test
    @DisplayName("queryOrdersByCursor - 返回 size+1 条则 hasMore=true 并截断")
    void queryOrdersByCursor_hasMore() {
        loginAs(CURRENT_USER, "USER");
        CursorPageRequest req = new CursorPageRequest();
        req.setLastId(0L);
        req.setSize(2);
        // fetchSize = size+1 = 3，返回 3 条 → hasMore
        given(orderMapper.selectByCursor(0L, CURRENT_USER, 3))
                .willReturn(List.of(
                        buildOrder(3L, CURRENT_USER, OrderStatus.PENDING, null),
                        buildOrder(2L, CURRENT_USER, OrderStatus.PENDING, null),
                        buildOrder(1L, CURRENT_USER, OrderStatus.PENDING, null)));

        CursorPageResponse<OrderResponse> result = orderService.queryOrdersByCursor(req);

        BDDAssertions.then(result.isHasMore()).isTrue();
        BDDAssertions.then(result.getRecords()).hasSize(2);
        BDDAssertions.then(result.getNextLastId()).isEqualTo(2L);
        // 非管理员强制查本人（userId=1）
        then(orderMapper).should().selectByCursor(0L, CURRENT_USER, 3);
    }

    @Test
    @DisplayName("queryOrdersByCursor - 返回 ≤size 条则 hasMore=false，nextLastId=最后一条")
    void queryOrdersByCursor_noMore() {
        loginAs(1L, "ADMIN");
        CursorPageRequest req = new CursorPageRequest();
        req.setLastId(10L);
        req.setSize(2);
        // ADMIN 查全部（userId=null）
        given(orderMapper.selectByCursor(10L, null, 3))
                .willReturn(List.of(
                        buildOrder(12L, 1L, OrderStatus.PENDING, null),
                        buildOrder(11L, 1L, OrderStatus.PENDING, null)));

        CursorPageResponse<OrderResponse> result = orderService.queryOrdersByCursor(req);

        BDDAssertions.then(result.isHasMore()).isFalse();
        BDDAssertions.then(result.getRecords()).hasSize(2);
        BDDAssertions.then(result.getNextLastId()).isEqualTo(11L);
        then(orderMapper).should().selectByCursor(10L, null, 3);
    }
}
