package com.example.project.controller;

import com.example.project.common.Result;
import com.example.project.dto.request.CartAddRequest;
import com.example.project.dto.response.CartItemResponse;
import com.example.project.security.SecurityUtil;
import com.example.project.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 购物车接口（需登录）
 * <p>
 * 购物车仅服务商城端（USER）；管理员同样可用（无角色限制）。
 * 结算复用 POST /api/orders（items 从购物车勾选项组装）。
 */
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
@Tag(name = "购物车", description = "购物车增删改查（需登录）")
public class CartController {

    private final CartService cartService;

    @GetMapping
    @Operation(summary = "购物车列表", security = @SecurityRequirement(name = "Bearer"))
    public Result<List<CartItemResponse>> list() {
        Long userId = SecurityUtil.getCurrentUserId();
        return Result.success(cartService.listCart(userId));
    }

    @GetMapping("/count")
    @Operation(summary = "购物车商品总数", description = "导航角标用", security = @SecurityRequirement(name = "Bearer"))
    public Result<Integer> count() {
        Long userId = SecurityUtil.getCurrentUserId();
        return Result.success(cartService.countCart(userId));
    }

    @PostMapping
    @Operation(summary = "加入购物车", security = @SecurityRequirement(name = "Bearer"))
    public Result<Void> add(@Valid @RequestBody CartAddRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        cartService.addCart(userId, request);
        return Result.success();
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改数量", security = @SecurityRequirement(name = "Bearer"))
    public Result<Void> updateQuantity(@PathVariable Long id, @RequestParam Integer quantity) {
        Long userId = SecurityUtil.getCurrentUserId();
        cartService.updateQuantity(userId, id, quantity);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除条目", security = @SecurityRequirement(name = "Bearer"))
    public Result<Void> remove(@PathVariable Long id) {
        Long userId = SecurityUtil.getCurrentUserId();
        cartService.removeItem(userId, id);
        return Result.success();
    }
}
