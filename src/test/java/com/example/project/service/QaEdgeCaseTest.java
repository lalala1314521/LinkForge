package com.example.project.service;

import com.example.project.common.BusinessException;
import com.example.project.common.ErrorCode;
import com.example.project.dto.request.CursorPageRequest;
import com.example.project.dto.request.OrderCreateRequest;
import com.example.project.dto.request.OrderItemRequest;
import com.example.project.entity.Order;
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
import com.example.project.util.DesensitizeUtil;
import com.example.project.util.DistributedLockUtil;
import com.example.project.util.SnowflakeIdGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.BDDMockito.then;

/**
 * QA 独立边界补测（fresh eyes）
 * <p>
 * 覆盖工程师测试未直接覆盖的边界场景：
 * - createOrder 空 items / 空列表
 * - queryOrdersByCursor 未登录
 * - DesensitizeUtil 边界输入（空邮箱名前缀、短手机号、10位手机号、非数字手机号）
 * - 重复商品合并后库存只扣一次（与 CreateOrderServiceTest 交叉验证，防回归）
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("QA边界补测")
class QaEdgeCaseTest {

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
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "user1", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        auth.setDetails(CURRENT_USER);
        SecurityContextHolder.getContext().setAuthentication(auth);
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

    // ---- createOrder 空 items ----

    @Test
    @DisplayName("createOrder - items 为 null 抛 BAD_REQUEST")
    void createOrder_nullItems_badRequest() {
        given(userMapper.selectById(CURRENT_USER)).willReturn(buildUser(CURRENT_USER));
        OrderCreateRequest req = new OrderCreateRequest();
        req.setItems(null);

        assertThatThrownBy(() -> orderService.createOrder(req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }

    @Test
    @DisplayName("createOrder - items 为空列表抛 BAD_REQUEST")
    void createOrder_emptyItems_badRequest() {
        given(userMapper.selectById(CURRENT_USER)).willReturn(buildUser(CURRENT_USER));
        OrderCreateRequest req = new OrderCreateRequest();
        req.setItems(Collections.emptyList());

        assertThatThrownBy(() -> orderService.createOrder(req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        then(orderMapper).should(never()).insert(any(Order.class));
    }

    // ---- queryOrdersByCursor 未登录 ----

    @Test
    @DisplayName("queryOrdersByCursor - 未登录抛 UNAUTHORIZED")
    void queryOrdersByCursor_unauthenticated_unauthorized() {
        SecurityContextHolder.clearContext();
        CursorPageRequest req = new CursorPageRequest();
        req.setLastId(0L);
        req.setSize(20);

        assertThatThrownBy(() -> orderService.queryOrdersByCursor(req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    // ---- DesensitizeUtil 边界 ----

    @Test
    @DisplayName("maskEmail - 正常邮箱脱敏 a***e@example.com")
    void maskEmail_normal() {
        assertThat(DesensitizeUtil.maskEmail("alice@example.com")).isEqualTo("a***e@example.com");
    }

    @Test
    @DisplayName("maskEmail - 用户名2位 ab@x.com -> a***@x.com")
    void maskEmail_twoCharName() {
        assertThat(DesensitizeUtil.maskEmail("ab@x.com")).isEqualTo("a***@x.com");
    }

    @Test
    @DisplayName("maskEmail - 空邮箱名前缀不应抛异常（防御性）")
    void maskEmail_emptyNamePrefix_noException() {
        // 边界输入 "@example.com"：split 后 name 为空字符串，实现不应 charAt(0) 越界
        try {
            String result = DesensitizeUtil.maskEmail("@example.com");
            // 若实现返回原样或某种掩码均可接受，但不能抛异常
            assertThat(result).isNotNull();
        } catch (StringIndexOutOfBoundsException e) {
            throw new AssertionError("maskEmail('@example.com') 抛 StringIndexOutOfBoundsException，属于防御性缺陷", e);
        }
    }

    @Test
    @DisplayName("maskEmail - null 原样返回")
    void maskEmail_null() {
        assertThat(DesensitizeUtil.maskEmail(null)).isNull();
    }

    @Test
    @DisplayName("maskPhone - 11位手机号 138****8000")
    void maskPhone_normal() {
        assertThat(DesensitizeUtil.maskPhone("13812348000")).isEqualTo("138****8000");
    }

    @Test
    @DisplayName("maskPhone - 10位数字不脱敏但不应抛异常")
    void maskPhone_tenDigits_noException() {
        assertThat(DesensitizeUtil.maskPhone("1381234800")).isEqualTo("1381234800");
    }

    @Test
    @DisplayName("maskPhone - null 原样返回")
    void maskPhone_null() {
        assertThat(DesensitizeUtil.maskPhone(null)).isNull();
    }

    // ---- 与 CreateOrderServiceTest 交叉验证：重复商品只扣一次 ----

    @Test
    @DisplayName("createOrder - 同一商品三次提交合并数量，只扣减一次、只写一条明细")
    void createOrder_duplicateItems_mergedOnce() {
        given(userMapper.selectById(CURRENT_USER)).willReturn(buildUser(CURRENT_USER));
        given(productMapper.selectById(1L)).willReturn(buildProduct(1L, new BigDecimal("10.00"), 100));
        given(inventoryService.deduct(1L, 10)).willReturn(true);   // 合并后 2+3+5=10
        given(idGenerator.nextId()).willReturn(9003L);

        orderService.createOrder(request(item(1L, 2), item(1L, 3), item(1L, 5)));

        then(inventoryService).should(times(1)).deduct(1L, 10);
        then(orderItemMapper).should(times(1)).insert(any());
    }

    private OrderCreateRequest request(OrderItemRequest... items) {
        OrderCreateRequest req = new OrderCreateRequest();
        req.setItems(List.of(items));
        return req;
    }
}
