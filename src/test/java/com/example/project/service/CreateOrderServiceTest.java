package com.example.project.service;

import com.example.project.common.BusinessException;
import com.example.project.common.ErrorCode;
import com.example.project.dto.request.OrderCreateRequest;
import com.example.project.dto.request.OrderItemRequest;
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
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

/**
 * createOrder 服务端计价与库存扣减测试（Service 层，纯 Mockito）
 * <p>
 * 用例：服务端计价（不信任前端金额）、库存不足整单回滚、商品不存在、同商品合并数量
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("创建订单服务测试")
class CreateOrderServiceTest {

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
        // 模拟登录用户（details=userId，权限 ROLE_USER），与 JwtAuthenticationFilter 行为一致
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "user1", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        auth.setDetails(CURRENT_USER);
        SecurityContextHolder.getContext().setAuthentication(auth);
        // lenient：lockBusy 用例会覆盖该 stub，避免 UnnecessaryStubbingException
        lenient().when(distributedLockUtil.tryLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class)))
                .thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
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

    private Product buildProduct(Long id, BigDecimal price, int stock) {
        Product p = new Product();
        p.setId(id);
        p.setName("商品" + id);
        p.setPrice(price);
        p.setStock(stock);
        p.setStatus("ON_SALE");
        return p;
    }

    private OrderItemRequest item(Long productId, int quantity) {
        OrderItemRequest req = new OrderItemRequest();
        req.setProductId(productId);
        req.setQuantity(quantity);
        return req;
    }

    private OrderCreateRequest request(OrderItemRequest... items) {
        OrderCreateRequest req = new OrderCreateRequest();
        req.setItems(List.of(items));
        return req;
    }

    /** 捕获 insert 的订单对象并回填 id（模拟 MyBatis useGeneratedKeys） */
    private ArgumentCaptor<Order> captureOrderAndSetId(Long id) {
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        org.mockito.Mockito.doAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(id);
            return null;
        }).when(orderMapper).insert(orderCaptor.capture());
        return orderCaptor;
    }

    // ---- 服务端计价 ----

    @Test
    @DisplayName("createOrder - 服务端按单价×数量计价并落库")
    void createOrder_serverSidePricing() {
        given(userMapper.selectById(CURRENT_USER)).willReturn(buildUser(CURRENT_USER));
        given(productMapper.selectById(1L)).willReturn(buildProduct(1L, new BigDecimal("10.00"), 100));
        given(productMapper.selectById(2L)).willReturn(buildProduct(2L, new BigDecimal("5.50"), 100));
        given(inventoryService.deduct(1L, 2)).willReturn(true);
        given(inventoryService.deduct(2L, 3)).willReturn(true);
        given(idGenerator.nextId()).willReturn(9001L);
        ArgumentCaptor<Order> orderCaptor = captureOrderAndSetId(100L);

        Long orderId = orderService.createOrder(request(item(1L, 2), item(2L, 3)));

        BDDAssertions.then(orderId).isEqualTo(100L);
        // 总价 = 10.00×2 + 5.50×3 = 36.50；finalAmount = totalAmount（优惠券未启用）
        Order saved = orderCaptor.getValue();
        BDDAssertions.then(saved.getUserId()).isEqualTo(CURRENT_USER);
        BDDAssertions.then(saved.getTotalAmount()).isEqualByComparingTo("36.50");
        BDDAssertions.then(saved.getFinalAmount()).isEqualByComparingTo("36.50");
        BDDAssertions.then(saved.getCouponDiscount()).isEqualByComparingTo("0");
        BDDAssertions.then(saved.getStatus()).isEqualTo(OrderStatus.PENDING);
        // 明细两条，订单ID回填
        ArgumentCaptor<OrderItem> itemCaptor = ArgumentCaptor.forClass(OrderItem.class);
        then(orderItemMapper).should(times(2)).insert(itemCaptor.capture());
        BDDAssertions.then(itemCaptor.getAllValues()).allMatch(i -> i.getOrderId().equals(100L));
    }

    // ---- 库存不足回滚 ----

    @Test
    @DisplayName("createOrder - 库存不足抛 STOCK_INSUFFICIENT，不落订单")
    void createOrder_insufficientStock_rollsBack() {
        given(userMapper.selectById(CURRENT_USER)).willReturn(buildUser(CURRENT_USER));
        given(productMapper.selectById(1L)).willReturn(buildProduct(1L, new BigDecimal("10.00"), 5));
        given(inventoryService.deduct(1L, 2)).willReturn(true);   // 第一个商品扣减成功
        given(productMapper.selectById(2L)).willReturn(buildProduct(2L, new BigDecimal("5.50"), 3));
        given(inventoryService.deduct(2L, 5)).willReturn(false);  // 第二个商品库存不足

        assertThatThrownBy(() -> orderService.createOrder(request(item(1L, 2), item(2L, 5))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.STOCK_INSUFFICIENT);

        // 事务回滚：不落订单、不写明细
        then(orderMapper).should(never()).insert(any(Order.class));
        then(orderItemMapper).should(never()).insert(any(OrderItem.class));
    }

    // ---- 商品不存在 ----

    @Test
    @DisplayName("createOrder - 商品不存在或已下架抛 PRODUCT_NOT_FOUND")
    void createOrder_productNotFound() {
        given(userMapper.selectById(CURRENT_USER)).willReturn(buildUser(CURRENT_USER));
        given(productMapper.selectById(99L)).willReturn(null);

        assertThatThrownBy(() -> orderService.createOrder(request(item(99L, 1))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);

        then(orderMapper).should(never()).insert(any(Order.class));
    }

    @Test
    @DisplayName("createOrder - 商品已下架抛 PRODUCT_NOT_FOUND")
    void createOrder_productOffSale() {
        given(userMapper.selectById(CURRENT_USER)).willReturn(buildUser(CURRENT_USER));
        Product offSale = buildProduct(1L, new BigDecimal("10.00"), 100);
        offSale.setStatus("OFF_SALE");
        given(productMapper.selectById(1L)).willReturn(offSale);

        assertThatThrownBy(() -> orderService.createOrder(request(item(1L, 1))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
    }

    // ---- 同商品合并 ----

    @Test
    @DisplayName("createOrder - 同商品多次提交合并数量，只扣减一次")
    void createOrder_duplicateItems_merged() {
        given(userMapper.selectById(CURRENT_USER)).willReturn(buildUser(CURRENT_USER));
        given(productMapper.selectById(1L)).willReturn(buildProduct(1L, new BigDecimal("10.00"), 100));
        given(inventoryService.deduct(1L, 5)).willReturn(true);   // 合并后数量=2+3=5
        given(idGenerator.nextId()).willReturn(9002L);
        ArgumentCaptor<Order> orderCaptor = captureOrderAndSetId(101L);

        orderService.createOrder(request(item(1L, 2), item(1L, 3)));

        // 只扣减一次（合并后 quantity=5）
        then(inventoryService).should(times(1)).deduct(1L, 5);
        // 只写一条明细
        then(orderItemMapper).should(times(1)).insert(any(OrderItem.class));
        // 总价 = 10.00×5 = 50.00
        BDDAssertions.then(orderCaptor.getValue().getTotalAmount()).isEqualByComparingTo("50.00");
    }

    // ---- 分布式锁失败 ----

    @Test
    @DisplayName("createOrder - 获取分布式锁失败抛 ORDER_CREATE_BUSY")
    void createOrder_lockBusy() {
        given(userMapper.selectById(CURRENT_USER)).willReturn(buildUser(CURRENT_USER));
        given(distributedLockUtil.tryLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class)))
                .willReturn(false);

        assertThatThrownBy(() -> orderService.createOrder(request(item(1L, 1))))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_CREATE_BUSY);

        then(orderMapper).should(never()).insert(any(Order.class));
    }
}
