package com.example.project.service;

import com.example.project.common.BusinessException;
import com.example.project.common.ErrorCode;
import com.example.project.config.BloomFilterConfig;
import com.example.project.dto.request.SeckillActivityCreateRequest;
import com.example.project.dto.response.SeckillResult;
import com.example.project.entity.Product;
import com.example.project.entity.SeckillActivity;
import com.example.project.mapper.ProductMapper;
import com.example.project.mapper.SeckillActivityMapper;
import com.example.project.mapper.SeckillOrderMapper;
import com.example.project.mq.dto.SeckillOrderMessage;
import com.example.project.mq.producer.SeckillKafkaProducer;
import com.example.project.service.impl.SeckillServiceImpl;
import com.example.project.util.LuaScriptExecutor;
import com.example.project.util.SnowflakeIdGenerator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBloomFilter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.BDDAssertions.then;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 秒杀服务测试（纯 Mockito 单测）
 * <p>
 * 覆盖：Lua 返回码分支（1/-1/-2）、布隆拦截、时间窗、活动不存在、创建活动预热、雪花单号前缀 "SK"。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("秒杀服务测试")
class SeckillServiceTest {

    @Mock SeckillActivityMapper seckillActivityMapper;
    @Mock SeckillOrderMapper seckillOrderMapper;
    @Mock ProductMapper productMapper;
    @Mock LuaScriptExecutor luaScriptExecutor;
    @Mock BloomFilterConfig bloomFilterConfig;
    @Mock SeckillKafkaProducer seckillKafkaProducer;
    @Mock SnowflakeIdGenerator idGenerator;
    @Mock StringRedisTemplate stringRedisTemplate;
    @Mock ValueOperations<String, String> valueOperations;
    @Mock RBloomFilter<String> bloomFilter;

    @InjectMocks SeckillServiceImpl seckillService;

    private static final Long CURRENT_USER = 1L;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "user1", null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        auth.setDetails(CURRENT_USER);
        SecurityContextHolder.getContext().setAuthentication(auth);
        // createActivity 预热需要 opsForValue；doSeckill 用例不使用（lenient 避免 UnnecessaryStubbing）
        org.mockito.Mockito.lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private SeckillActivity buildActivity(Long id, LocalDateTime start, LocalDateTime end) {
        SeckillActivity a = new SeckillActivity();
        a.setId(id);
        a.setName("测试秒杀");
        a.setProductId(100L);
        a.setSeckillPrice(new BigDecimal("9.90"));
        a.setTotalStock(100);
        a.setAvailableStock(100);
        a.setStartTime(start);
        a.setEndTime(end);
        a.setStatus("ACTIVE");
        return a;
    }

    private SeckillActivity activeActivity(Long id) {
        return buildActivity(id, LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1));
    }

    // ---- Lua 返回码分支 ----

    @Test
    @DisplayName("doSeckill - 正常流：Lua 返回1，雪花单号 SK 前缀，直发 Kafka")
    void doSeckill_success() {
        given(seckillActivityMapper.selectById(1L)).willReturn(activeActivity(1L));
        given(bloomFilterConfig.getBloomFilter(1L)).willReturn(bloomFilter);
        given(bloomFilter.contains("1")).willReturn(false);
        given(luaScriptExecutor.executeSeckillDecrement(1L, 1L)).willReturn(1L);
        given(idGenerator.nextId()).willReturn(90001L);

        SeckillResult result = seckillService.doSeckill(1L);

        then(result.getOrderNo()).isEqualTo("SK90001");
        then(result.getSeckillPrice()).isEqualByComparingTo("9.90");
        then(result.getStatus()).isEqualTo("PENDING");
        // 抢购成功进布隆
        verify(bloomFilter).add("1");
        // 消息携带真实秒杀价直发
        verify(seckillKafkaProducer).sendSeckillOrder(any(SeckillOrderMessage.class));
    }

    @Test
    @DisplayName("doSeckill - Lua 返回 -1：重复抢购")
    void doSeckill_repeat() {
        given(seckillActivityMapper.selectById(1L)).willReturn(activeActivity(1L));
        given(bloomFilterConfig.getBloomFilter(1L)).willReturn(bloomFilter);
        given(bloomFilter.contains("1")).willReturn(false);
        given(luaScriptExecutor.executeSeckillDecrement(1L, 1L)).willReturn(-1L);

        assertThatThrownBy(() -> seckillService.doSeckill(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SECKILL_REPEAT);
        verify(seckillKafkaProducer, never()).sendSeckillOrder(any());
    }

    @Test
    @DisplayName("doSeckill - Lua 返回 -2：售罄/key 缺失（fail-closed）")
    void doSeckill_stockEmpty() {
        given(seckillActivityMapper.selectById(1L)).willReturn(activeActivity(1L));
        given(bloomFilterConfig.getBloomFilter(1L)).willReturn(bloomFilter);
        given(bloomFilter.contains("1")).willReturn(false);
        given(luaScriptExecutor.executeSeckillDecrement(1L, 1L)).willReturn(-2L);

        assertThatThrownBy(() -> seckillService.doSeckill(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SECKILL_STOCK_EMPTY);
        verify(seckillKafkaProducer, never()).sendSeckillOrder(any());
    }

    // ---- 布隆拦截 ----

    @Test
    @DisplayName("doSeckill - 布隆命中疑似重复：直接拦截，不打 Lua")
    void doSeckill_bloomBlock() {
        given(seckillActivityMapper.selectById(1L)).willReturn(activeActivity(1L));
        given(bloomFilterConfig.getBloomFilter(1L)).willReturn(bloomFilter);
        given(bloomFilter.contains("1")).willReturn(true);

        assertThatThrownBy(() -> seckillService.doSeckill(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SECKILL_REPEAT);
        verify(luaScriptExecutor, never()).executeSeckillDecrement(anyLong(), anyLong());
    }

    // ---- 活动不存在 / 时间窗 ----

    @Test
    @DisplayName("doSeckill - 活动不存在")
    void doSeckill_activityNotFound() {
        given(seckillActivityMapper.selectById(99L)).willReturn(null);

        assertThatThrownBy(() -> seckillService.doSeckill(99L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SECKILL_ACTIVITY_NOT_FOUND);
    }

    @Test
    @DisplayName("doSeckill - 未开始返回 1323")
    void doSeckill_notStarted() {
        given(seckillActivityMapper.selectById(1L)).willReturn(
                buildActivity(1L, LocalDateTime.now().plusHours(1), LocalDateTime.now().plusHours(2)));

        assertThatThrownBy(() -> seckillService.doSeckill(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SEC_ACTIVITY_NOT_STARTED);
    }

    @Test
    @DisplayName("doSeckill - 已结束返回 1324")
    void doSeckill_ended() {
        given(seckillActivityMapper.selectById(1L)).willReturn(
                buildActivity(1L, LocalDateTime.now().minusHours(2), LocalDateTime.now().minusHours(1)));

        assertThatThrownBy(() -> seckillService.doSeckill(1L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.SEC_ACTIVITY_ENDED);
    }

    // ---- 创建活动（预热 + 布隆 init）----

    @Test
    @DisplayName("createActivity - 成功：校验商品、落库、预热库存（TTL 设置）、布隆 init")
    void createActivity_success() {
        Product product = new Product();
        product.setId(100L);
        product.setName("耳机");
        product.setPrice(new BigDecimal("199.00"));
        product.setStock(50);
        product.setStatus("ON_SALE");
        given(productMapper.selectById(100L)).willReturn(product);
        given(bloomFilterConfig.getBloomFilter(1L)).willReturn(bloomFilter);
        org.mockito.Mockito.doAnswer(invocation -> {
            SeckillActivity a = invocation.getArgument(0);
            a.setId(1L);
            return null;
        }).when(seckillActivityMapper).insert(any(SeckillActivity.class));

        SeckillActivityCreateRequest req = new SeckillActivityCreateRequest();
        req.setName("测试秒杀");
        req.setProductId(100L);
        req.setSeckillPrice(new BigDecimal("9.90"));
        req.setTotalStock(100);
        req.setStartTime(LocalDateTime.now().plusHours(1));
        req.setEndTime(LocalDateTime.now().plusDays(1));

        Long id = seckillService.createActivity(req);

        then(id).isEqualTo(1L);
        // 预热：SET stock key，TTL 单位秒
        verify(valueOperations).set(eq("seckill:stock:1"), eq("100"), anyLong(), eq(TimeUnit.SECONDS));
        verify(bloomFilterConfig).getBloomFilter(1L);
    }

    @Test
    @DisplayName("createActivity - 商品不存在或下架返回 PRODUCT_NOT_FOUND")
    void createActivity_productNotFound() {
        given(productMapper.selectById(100L)).willReturn(null);

        SeckillActivityCreateRequest req = new SeckillActivityCreateRequest();
        req.setName("测试秒杀");
        req.setProductId(100L);
        req.setSeckillPrice(new BigDecimal("9.90"));
        req.setTotalStock(100);
        req.setStartTime(LocalDateTime.now().plusHours(1));
        req.setEndTime(LocalDateTime.now().plusDays(1));

        assertThatThrownBy(() -> seckillService.createActivity(req))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.PRODUCT_NOT_FOUND);
        verify(seckillActivityMapper, never()).insert(any());
    }

    @Test
    @DisplayName("getSeckillOrderStatus - 未找到订单返回 ORDER_NOT_FOUND")
    void getSeckillOrderStatus_notFound() {
        given(seckillOrderMapper.selectByOrderNo("SK1")).willReturn(null);

        assertThatThrownBy(() -> seckillService.getSeckillOrderStatus("SK1"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ORDER_NOT_FOUND);
    }
}
