package com.example.project.service;

import com.example.project.common.PageResult;
import com.example.project.dto.request.SeckillActivityCreateRequest;
import com.example.project.dto.request.SeckillActivityQueryRequest;
import com.example.project.dto.response.SeckillActivityResponse;
import com.example.project.dto.response.SeckillResult;

import java.util.List;

/**
 * 秒杀服务接口
 * <p>
 * 热路径（doSeckill）不碰 DB：时间窗 → 布隆 → Lua（Redis 原子库存+去重）→ 雪花 → Kafka 直发。
 * 活动管理（createActivity/updateActivityStatus）负责预热 Redis 库存（TTL 覆盖活动期）与布隆 init。
 */
public interface SeckillService {

    /**
     * 用户抢购（userId 从 SecurityUtil 取，不信任前端）：成功返回订单号（PENDING 待支付）
     */
    SeckillResult doSeckill(Long activityId);

    /**
     * 秒杀订单状态查询
     */
    SeckillResult getSeckillOrderStatus(String orderNo);

    /**
     * 管理：创建活动（校验商品存在/ON_SALE → insert → 预热库存 + 布隆 init）
     */
    Long createActivity(SeckillActivityCreateRequest request);

    /**
     * 管理：上下架（ACTIVATE 重新预热 / ENDED 清 Redis）
     */
    void updateActivityStatus(Long activityId, String targetStatus);

    /**
     * 在售活动列表（状态=ACTIVE，用户端）
     */
    List<SeckillActivityResponse> listActivities();

    /**
     * 管理端分页查询
     */
    PageResult<SeckillActivityResponse> queryActivities(SeckillActivityQueryRequest request);
}
