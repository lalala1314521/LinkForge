package com.example.project.service;

import com.example.project.common.PageResult;
import com.example.project.dto.request.SeckillActivityCreateRequest;
import com.example.project.dto.request.SeckillActivityQueryRequest;
import com.example.project.dto.response.SeckillActivityResponse;
import com.example.project.dto.response.SeckillOrderResponse;
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
     * 秒杀订单状态查询（归属校验：只能查自己的单）
     */
    SeckillResult getSeckillOrderStatus(String orderNo);

    /**
     * 秒杀订单支付：PENDING→PAID（归属校验 + 条件更新防并发 + 幂等），支付成功发放积分
     */
    void paySeckillOrder(String orderNo);

    /**
     * 秒杀订单取消：PENDING→CANCELLED（归属校验 + 条件更新），成功回加 Redis 库存 + DB 库存 + 释放限购名额
     */
    void cancelSeckillOrder(String orderNo);

    /**
     * 超时关单任务用：无归属校验，PENDING→CANCELLED + 回库存（内部方法）
     */
    void cancelSeckillOrderByTimeout(String orderNo);

    /**
     * 我的秒杀订单列表（userId 从 SecurityUtil 取，不信任前端；含活动名/商品名）
     */
    List<SeckillOrderResponse> getMyOrders();

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
