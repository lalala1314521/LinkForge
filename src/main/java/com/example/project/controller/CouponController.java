package com.example.project.controller;

import com.example.project.common.Result;
import com.example.project.annotation.AuditLog;
import com.example.project.dto.request.CouponCreateRequest;
import com.example.project.dto.response.CouponResponse;
import com.example.project.entity.Coupon;
import com.example.project.security.SecurityUtil;
import com.example.project.service.CouponService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 优惠券接口（最小可用集）
 * <p>
 * POST /api/coupons 建券模板 ADMIN-only（SecurityConfig 配置）；
 * 领券/我的券/可领列表登录即可访问。
 */
@RestController
@RequestMapping("/api/coupons")
@RequiredArgsConstructor
@Tag(name = "优惠券管理", description = "领券、我的优惠券、管理员建券")
public class CouponController {

    private final CouponService couponService;

    @PostMapping
    @AuditLog(action = "CREATE_COUPON", targetType = "COUPON")
    @Operation(summary = "创建优惠券模板", description = "管理员创建优惠券模板", security = @SecurityRequirement(name = "Bearer"))
    public Result<Long> createCoupon(@Valid @RequestBody CouponCreateRequest request) {
        return Result.success(couponService.createCoupon(request));
    }

    @GetMapping
    @Operation(summary = "可领取优惠券列表", description = "未过期/未领完/ACTIVE", security = @SecurityRequirement(name = "Bearer"))
    public Result<List<Coupon>> listAvailable() {
        return Result.success(couponService.listAvailable());
    }

    @PostMapping("/{id}/claim")
    @Operation(summary = "领取优惠券", description = "每用户每模板限领1张；重复领返回1305；领完返回1306", security = @SecurityRequirement(name = "Bearer"))
    public Result<Long> claim(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        return Result.success(couponService.claim(userId, id));
    }

    @GetMapping("/mine")
    @Operation(summary = "我的优惠券", description = "可按状态过滤（UNUSED/FROZEN/USED）", security = @SecurityRequirement(name = "Bearer"))
    public Result<List<CouponResponse>> myCoupons(@RequestParam(required = false) String status) {
        Long userId = SecurityUtil.getCurrentUserId();
        return Result.success(couponService.listMyCoupons(userId, status));
    }
}
