package com.example.project.service.impl;

import com.example.project.common.BusinessException;
import com.example.project.common.ErrorCode;
import com.example.project.common.PageResult;
import com.example.project.config.BloomFilterConfig;
import com.example.project.dto.request.SeckillActivityCreateRequest;
import com.example.project.dto.request.SeckillActivityQueryRequest;
import com.example.project.dto.response.SeckillActivityResponse;
import com.example.project.dto.response.SeckillResult;
import com.example.project.entity.Product;
import com.example.project.entity.SeckillActivity;
import com.example.project.entity.SeckillOrder;
import com.example.project.mapper.ProductMapper;
import com.example.project.mapper.SeckillActivityMapper;
import com.example.project.mapper.SeckillOrderMapper;
import com.example.project.mq.dto.SeckillOrderMessage;
import com.example.project.mq.producer.SeckillKafkaProducer;
import com.example.project.security.SecurityUtil;
import com.example.project.service.SeckillService;
import com.example.project.util.LuaScriptExecutor;
import com.example.project.util.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 秒杀服务实现
 * <p>
 * 核心链路（D1/D4/D5/D6/D8/D9）：
 * - 时间窗校验（应用层，毫秒误差可接受）
 * - 布隆 contains 快速拦截（防刷，非权威）
 * - Lua 原子「库存预扣 + 用户去重」（权威限购）
 * - 雪花单号 "SK"+id + Kafka 直发（热路径不碰 DB）
 * <p>
 * Redis key 命名：seckill:stock:{id}（String）/ seckill:users:{id}（Set）/ seckill:bloom:{id}（布隆）。
 * 序列化：一律 StringRedisTemplate（Jackson3 @class 会污染 Lua tonumber）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillServiceImpl implements SeckillService {

    private final SeckillActivityMapper seckillActivityMapper;
    private final SeckillOrderMapper seckillOrderMapper;
    private final ProductMapper productMapper;
    private final LuaScriptExecutor luaScriptExecutor;
    private final BloomFilterConfig bloomFilterConfig;
    private final SeckillKafkaProducer seckillKafkaProducer;
    private final SnowflakeIdGenerator idGenerator;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public SeckillResult doSeckill(Long activityId) {
        Long userId = SecurityUtil.getCurrentUserId();   // JWT 取用户，不信任前端
        SeckillActivity activity = seckillActivityMapper.selectById(activityId);
        if (activity == null) {
            throw new BusinessException(ErrorCode.SECKILL_ACTIVITY_NOT_FOUND);
        }

        // 时间窗（应用层）
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(activity.getStartTime())) {
            throw new BusinessException(ErrorCode.SEC_ACTIVITY_NOT_STARTED);
        }
        if (now.isAfter(activity.getEndTime())) {
            throw new BusinessException(ErrorCode.SEC_ACTIVITY_ENDED);
        }

        // 布隆快速拦截（防刷，非权威；误判仅误伤极小比例）
        RBloomFilter<String> bloom = bloomFilterConfig.getBloomFilter(activityId);
        if (bloom.contains(String.valueOf(userId))) {
            throw new BusinessException(ErrorCode.SECKILL_REPEAT);
        }

        // Lua 原子：库存预扣 + 用户去重（权威限购）
        long code = luaScriptExecutor.executeSeckillDecrement(activityId, userId);
        if (code == -1) {
            throw new BusinessException(ErrorCode.SECKILL_REPEAT);
        }
        if (code == -2) {
            throw new BusinessException(ErrorCode.SECKILL_STOCK_EMPTY); // fail-closed：key 缺失视为售罄
        }

        // 抢购成功 → 进布隆（后续请求快速拦截）
        bloom.add(String.valueOf(userId));

        // 雪花单号（"SK" 前缀标识秒杀单，长度 < order_no VARCHAR(32)）
        String orderNo = "SK" + idGenerator.nextId();

        SeckillOrderMessage msg = SeckillOrderMessage.builder()
                .orderNo(orderNo)
                .userId(userId)
                .activityId(activityId)
                .productId(activity.getProductId())
                .price(activity.getSeckillPrice())   // ★ 真实秒杀价（D2）
                .timestamp(System.currentTimeMillis())
                .build();
        seckillKafkaProducer.sendSeckillOrder(msg);  // 直发 Kafka（热路径不碰 DB，D6）

        return new SeckillResult(orderNo, activityId, activity.getProductId(),
                activity.getSeckillPrice(), "PENDING");
    }

    @Override
    public SeckillResult getSeckillOrderStatus(String orderNo) {
        SeckillOrder order = seckillOrderMapper.selectByOrderNo(orderNo);
        if (order == null) {
            throw new BusinessException(ErrorCode.ORDER_NOT_FOUND);
        }
        return new SeckillResult(order.getOrderNo(), order.getActivityId(),
                order.getProductId(), order.getPrice(), order.getStatus());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createActivity(SeckillActivityCreateRequest request) {
        // 校验商品存在且 ON_SALE（仅校验，不扣 products.stock，D9）
        Product product = productMapper.selectById(request.getProductId());
        if (product == null || !"ON_SALE".equals(product.getStatus())) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND);
        }
        if (request.getSeckillPrice() == null || request.getSeckillPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "秒杀价格必须大于0");
        }
        if (request.getTotalStock() == null || request.getTotalStock() <= 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "秒杀总库存必须大于0");
        }
        if (!request.getStartTime().isBefore(request.getEndTime())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "开始时间必须早于结束时间");
        }

        SeckillActivity activity = new SeckillActivity();
        activity.setName(request.getName());
        activity.setProductId(request.getProductId());
        activity.setSeckillPrice(request.getSeckillPrice());
        activity.setTotalStock(request.getTotalStock());
        activity.setAvailableStock(request.getTotalStock());
        activity.setStartTime(request.getStartTime());
        activity.setEndTime(request.getEndTime());
        activity.setStatus("CREATED");
        seckillActivityMapper.insert(activity);   // 回填 id

        // 创建即预热：TTL = end_time - now 覆盖活动期（D1）；布隆 init（D5）
        warmUpStock(activity);
        bloomFilterConfig.getBloomFilter(activity.getId());   // 内部 tryInit（幂等）
        log.info("[秒杀] 活动创建成功：id={}, name={}, seckillPrice={}, totalStock={}",
                activity.getId(), activity.getName(), activity.getSeckillPrice(), activity.getTotalStock());
        return activity.getId();
    }

    @Override
    public void updateActivityStatus(Long activityId, String targetStatus) {
        SeckillActivity activity = seckillActivityMapper.selectById(activityId);
        if (activity == null) {
            throw new BusinessException(ErrorCode.SECKILL_ACTIVITY_NOT_FOUND);
        }
        if (!List.of("ACTIVE", "ENDED").contains(targetStatus)) {
            throw new BusinessException(ErrorCode.SECKILL_STATUS_INVALID);
        }
        seckillActivityMapper.updateStatus(activityId, targetStatus);
        if ("ACTIVE".equals(targetStatus)) {
            warmUpStock(activity);   // 上架确保预热（幂等 SET，TTL 重新覆盖活动期）
        }
        if ("ENDED".equals(targetStatus)) {
            stringRedisTemplate.delete(stockKey(activityId));   // 下架清 Redis（以 DB 为准）
            stringRedisTemplate.delete(usersKey(activityId));
        }
        log.info("[秒杀] 活动{} 状态更新为 {}", activityId, targetStatus);
    }

    @Override
    public List<SeckillActivityResponse> listActivities() {
        return seckillActivityMapper.selectActive().stream().map(this::toResponse).toList();
    }

    @Override
    public PageResult<SeckillActivityResponse> queryActivities(SeckillActivityQueryRequest request) {
        int page = request.getPage() == null ? 1 : request.getPage();
        int size = request.getSize() == null ? 10 : request.getSize();
        int offset = (page - 1) * size;
        List<SeckillActivity> activities = seckillActivityMapper.selectPage(request.getStatus(), offset, size);
        long total = seckillActivityMapper.countPage(request.getStatus());
        List<SeckillActivityResponse> responses = activities.stream().map(this::toResponse).toList();
        return new PageResult<>(responses, total, page, size);
    }

    /**
     * 预热库存：SET seckill:stock:{id}=totalStock，TTL = end_time - now（覆盖整个活动期，活动期内不过期）
     */
    private void warmUpStock(SeckillActivity activity) {
        long ttlSeconds = Duration.between(LocalDateTime.now(), activity.getEndTime()).getSeconds();
        if (ttlSeconds <= 0) {
            ttlSeconds = 1;   // 防御：已结束给最小 TTL
        }
        stringRedisTemplate.opsForValue().set(stockKey(activity.getId()),
                String.valueOf(activity.getTotalStock()), ttlSeconds, TimeUnit.SECONDS);
        log.info("[秒杀] 预热库存：activityId={}, totalStock={}, ttl={}s", activity.getId(), activity.getTotalStock(), ttlSeconds);
    }

    private String stockKey(Long activityId) {
        return "seckill:stock:" + activityId;
    }

    private String usersKey(Long activityId) {
        return "seckill:users:" + activityId;
    }

    private SeckillActivityResponse toResponse(SeckillActivity activity) {
        SeckillActivityResponse response = new SeckillActivityResponse();
        response.setId(activity.getId());
        response.setName(activity.getName());
        response.setProductId(activity.getProductId());
        response.setSeckillPrice(activity.getSeckillPrice());
        response.setTotalStock(activity.getTotalStock());
        response.setAvailableStock(activity.getAvailableStock());
        response.setStartTime(activity.getStartTime());
        response.setEndTime(activity.getEndTime());
        response.setStatus(activity.getStatus());
        return response;
    }
}
