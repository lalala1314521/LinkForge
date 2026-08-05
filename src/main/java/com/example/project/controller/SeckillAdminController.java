package com.example.project.controller;

import com.example.project.common.PageResult;
import com.example.project.common.Result;
import com.example.project.annotation.AuditLog;
import com.example.project.dto.request.SeckillActivityCreateRequest;
import com.example.project.dto.request.SeckillActivityQueryRequest;
import com.example.project.dto.request.SeckillActivityStatusRequest;
import com.example.project.dto.response.SeckillActivityResponse;
import com.example.project.service.SeckillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 秒杀管理接口（ADMIN-only，SecurityConfig /api/seckill/admin/**）
 */
@RestController
@RequestMapping("/api/seckill/admin")
@RequiredArgsConstructor
@Tag(name = "秒杀管理", description = "秒杀活动管理（ADMIN）")
public class SeckillAdminController {

    private final SeckillService seckillService;

    @PostMapping("/activities")
    @AuditLog(action = "CREATE_SECKILL_ACTIVITY", targetType = "SECKILL_ACTIVITY")
    @Operation(summary = "创建秒杀活动", description = "校验商品存在/ON_SALE → 落库 → 预热 Redis 库存（TTL 覆盖活动期）→ 布隆 init", security = @SecurityRequirement(name = "Bearer"))
    public Result<Long> createActivity(@Valid @RequestBody SeckillActivityCreateRequest request) {
        return Result.success(seckillService.createActivity(request));
    }

    @PutMapping("/activities/{id}/status")
    @AuditLog(action = "UPDATE_SECKILL_ACTIVITY_STATUS", targetType = "SECKILL_ACTIVITY")
    @Operation(summary = "上下架秒杀活动", description = "ACTIVE=上架（重新预热）/ ENDED=下架（清 Redis）", security = @SecurityRequirement(name = "Bearer"))
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody SeckillActivityStatusRequest request) {
        seckillService.updateActivityStatus(id, request.getTargetStatus());
        return Result.success();
    }

    @GetMapping("/activities")
    @Operation(summary = "秒杀活动分页查询", description = "可按状态过滤（CREATED/ACTIVE/ENDED）", security = @SecurityRequirement(name = "Bearer"))
    public Result<PageResult<SeckillActivityResponse>> queryActivities(@Valid SeckillActivityQueryRequest request) {
        return Result.success(seckillService.queryActivities(request));
    }
}
