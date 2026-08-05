package com.example.project.controller;

import com.example.project.common.Result;
import com.example.project.dto.response.SeckillActivityResponse;
import com.example.project.dto.response.SeckillOrderResponse;
import com.example.project.dto.response.SeckillResult;
import com.example.project.service.SeckillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 秒杀用户接口
 * <p>
 * 抢购/查询均需登录（userId 由 Service 内 SecurityUtil 取，不信任前端）。
 */
@RestController
@RequestMapping("/api/seckill")
@RequiredArgsConstructor
@Tag(name = "秒杀", description = "秒杀抢购与查询（需登录）")
public class SeckillController {

    private final SeckillService seckillService;

    @PostMapping("/{activityId}")
    @Operation(summary = "秒杀抢购", description = "抢购成功返回订单号（PENDING 待支付）；未开始/已结束/售罄/重复返回对应错误码", security = @SecurityRequirement(name = "Bearer"))
    public Result<SeckillResult> doSeckill(@PathVariable Long activityId) {
        return Result.success(seckillService.doSeckill(activityId));
    }

    @GetMapping("/order/{orderNo}")
    @Operation(summary = "秒杀订单状态查询", security = @SecurityRequirement(name = "Bearer"))
    public Result<SeckillResult> getOrderStatus(@PathVariable String orderNo) {
        return Result.success(seckillService.getSeckillOrderStatus(orderNo));
    }

    @GetMapping("/orders/mine")
    @Operation(summary = "我的秒杀订单列表", description = "按当前登录用户查询（含活动名/商品名），USER 可访问", security = @SecurityRequirement(name = "Bearer"))
    public Result<List<SeckillOrderResponse>> getMyOrders() {
        return Result.success(seckillService.getMyOrders());
    }

    @PostMapping("/orders/{orderNo}/pay")
    @Operation(summary = "秒杀订单支付", description = "PENDING→PAID，支付成功发放积分；已支付幂等返回成功", security = @SecurityRequirement(name = "Bearer"))
    public Result<Void> payOrder(@PathVariable String orderNo) {
        seckillService.paySeckillOrder(orderNo);
        return Result.success();
    }

    @PostMapping("/orders/{orderNo}/cancel")
    @Operation(summary = "秒杀订单取消", description = "PENDING→CANCELLED，回补库存并释放限购名额；已取消幂等返回成功", security = @SecurityRequirement(name = "Bearer"))
    public Result<Void> cancelOrder(@PathVariable String orderNo) {
        seckillService.cancelSeckillOrder(orderNo);
        return Result.success();
    }

    @PostMapping("/orders/{orderNo}/refund")
    @Operation(summary = "秒杀订单退款", description = "PAID→REFUNDED（已支付售后），回补库存并释放限购名额、积分回退；已退款幂等返回成功", security = @SecurityRequirement(name = "Bearer"))
    public Result<Void> refundOrder(@PathVariable String orderNo) {
        seckillService.refundSeckillOrder(orderNo);
        return Result.success();
    }

    @GetMapping("/activities")
    @Operation(summary = "在售秒杀活动列表", security = @SecurityRequirement(name = "Bearer"))
    public Result<List<SeckillActivityResponse>> listActivities() {
        return Result.success(seckillService.listActivities());
    }
}
