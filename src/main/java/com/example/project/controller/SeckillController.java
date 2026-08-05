package com.example.project.controller;

import com.example.project.common.Result;
import com.example.project.dto.response.SeckillActivityResponse;
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

    @GetMapping("/activities")
    @Operation(summary = "在售秒杀活动列表", security = @SecurityRequirement(name = "Bearer"))
    public Result<List<SeckillActivityResponse>> listActivities() {
        return Result.success(seckillService.listActivities());
    }
}
