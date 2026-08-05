package com.example.project.service;

import com.example.project.common.BusinessException;
import com.example.project.common.ErrorCode;
import com.example.project.common.PageResult;
import com.example.project.dto.request.OrderQueryRequest;
import com.example.project.dto.response.OrderResponse;
import com.example.project.entity.Order;
import com.example.project.enums.OrderStatus;
import com.example.project.mapper.OrderItemMapper;
import com.example.project.mapper.OrderMapper;
import com.example.project.mapper.ProductMapper;
import com.example.project.mapper.UserMapper;
import com.example.project.service.impl.OrderServiceImpl;
import com.example.project.util.DistributedLockUtil;
import com.example.project.util.SnowflakeIdGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.assertj.core.api.BDDAssertions;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * 订单归属/越权安全测试（Service 层）
 * <p>
 * 用例：B 操作 A 的资源 → 403；未登录 → 401；ADMIN 可操作任意订单；queryOrders 强制本人
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("订单归属安全测试")
class OrderSecurityTest {

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

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    /** 模拟登录：details 存 userId，权限 ROLE_<role>（与 JwtAuthenticationFilter 行为一致） */
    private void loginAs(Long userId, String role) {
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "user" + userId, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        auth.setDetails(userId);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    /** 构造归属于 ownerId 的订单 */
    private Order buildOrder(Long id, Long ownerId) {
        Order order = new Order();
        order.setId(id);
        order.setOrderNo("SN" + id);
        order.setUserId(ownerId);
        order.setTotalAmount(new BigDecimal("99.00"));
        order.setCouponDiscount(BigDecimal.ZERO);
        order.setFinalAmount(new BigDecimal("99.00"));
        order.setStatus(OrderStatus.PENDING);
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        return order;
    }

    // ---- 越权：B 操作 A 的资源 ----
    @Test
    @DisplayName("getOrderById - 用户B查用户A的订单返回 FORBIDDEN")
    void getOrderById_crossUser_forbidden() {
        loginAs(2L, "USER");
        given(orderMapper.selectById(1L)).willReturn(buildOrder(1L, 1L)); // 订单属于A

        assertThatThrownBy(() -> orderService.getOrderById(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("payOrder - 用户B支付用户A的订单返回 FORBIDDEN")
    void payOrder_crossUser_forbidden() {
        loginAs(2L, "USER");
        given(orderMapper.selectById(1L)).willReturn(buildOrder(1L, 1L));

        assertThatThrownBy(() -> orderService.payOrder(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
        // 越权时不允许更新状态
        then(orderMapper).should(never()).updateStatus(anyLong(), any(OrderStatus.class));
    }

    @Test
    @DisplayName("cancelOrder - 用户B取消用户A的订单返回 FORBIDDEN")
    void cancelOrder_crossUser_forbidden() {
        loginAs(2L, "USER");
        given(orderMapper.selectById(1L)).willReturn(buildOrder(1L, 1L));

        assertThatThrownBy(() -> orderService.cancelOrder(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.FORBIDDEN);
    }

    // ---- 未登录 ----

    @Test
    @DisplayName("getOrderById - 未登录返回 UNAUTHORIZED")
    void getOrderById_unauthenticated_unauthorized() {
        SecurityContextHolder.clearContext();
        given(orderMapper.selectById(1L)).willReturn(buildOrder(1L, 1L));

        assertThatThrownBy(() -> orderService.getOrderById(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    // ---- 管理员豁免 ----

    @Test
    @DisplayName("getOrderById - ADMIN 可查看任意用户的订单")
    void getOrderById_adminCanAccessAnyOrder() {
        loginAs(1L, "ADMIN");
        given(orderMapper.selectById(1L)).willReturn(buildOrder(1L, 2L)); // 订单属于B

        OrderResponse resp = orderService.getOrderById(1L);

        BDDAssertions.then(resp.getId()).isEqualTo(1L);
        BDDAssertions.then(resp.getUserId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("payOrder - ADMIN 可支付任意用户的订单")
    void payOrder_adminCanPayAnyOrder() {
        loginAs(1L, "ADMIN");
        given(orderMapper.selectById(1L)).willReturn(buildOrder(1L, 2L));

        orderService.payOrder(1L);

        then(orderMapper).should().updateStatus(1L, OrderStatus.PAID);
    }

    // ---- queryOrders 强制本人 ----

    @Test
    @DisplayName("queryOrders - 非管理员强制查询本人订单，忽略前端 userId 参数")
    void queryOrders_nonAdmin_forceOwnUserId() {
        loginAs(2L, "USER");
        OrderQueryRequest req = new OrderQueryRequest();
        req.setUserId(1L);          // 前端恶意传入他人 userId
        req.setPage(1);
        req.setSize(10);

        given(orderMapper.selectByCondition(any(), anyInt(), anyInt())).willReturn(List.of());
        given(orderMapper.countByCondition(any())).willReturn(0L);

        PageResult<OrderResponse> result = orderService.queryOrders(req);

        // 请求对象被强制改为当前用户ID
        ArgumentCaptor<OrderQueryRequest> captor = ArgumentCaptor.forClass(OrderQueryRequest.class);
        then(orderMapper).should().selectByCondition(captor.capture(), anyInt(), anyInt());
        BDDAssertions.then(captor.getValue().getUserId()).isEqualTo(2L);
        BDDAssertions.then(result.getTotal()).isEqualTo(0L);
    }

    @Test
    @DisplayName("queryOrders - ADMIN 保留前端 userId 过滤条件")
    void queryOrders_admin_keepsUserIdFilter() {
        loginAs(1L, "ADMIN");
        OrderQueryRequest req = new OrderQueryRequest();
        req.setUserId(2L);
        req.setPage(1);
        req.setSize(10);

        given(orderMapper.selectByCondition(any(), anyInt(), anyInt())).willReturn(List.of());
        given(orderMapper.countByCondition(any())).willReturn(0L);

        orderService.queryOrders(req);

        ArgumentCaptor<OrderQueryRequest> captor = ArgumentCaptor.forClass(OrderQueryRequest.class);
        then(orderMapper).should().selectByCondition(captor.capture(), anyInt(), anyInt());
        BDDAssertions.then(captor.getValue().getUserId()).isEqualTo(2L);
    }
}
