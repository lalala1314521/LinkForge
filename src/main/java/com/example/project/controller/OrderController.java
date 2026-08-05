package com.example.project.controller;

import com.example.project.common.BusinessException;
import com.example.project.common.ErrorCode;
import com.example.project.common.PageResult;
import com.example.project.common.Result;
import com.example.project.dto.request.CursorPageRequest;
import com.example.project.dto.request.OrderCreateRequest;
import com.example.project.dto.request.OrderQueryRequest;
import com.example.project.dto.response.CursorPageResponse;
import com.example.project.dto.response.OrderResponse;
import com.example.project.security.SecurityUtil;
import com.example.project.service.OrderService;
import com.example.project.util.IdempotencyUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 订单管理接口（RESTful 风格）
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "订单管理", description = "订单创建、查询、支付、取消相关接口（所有接口需认证）")
public class OrderController {

    private final OrderService orderService;
    private final IdempotencyUtil idempotencyUtil;

    /**
     * POST /api/orders
     * 创建订单（已集成分布式锁，同一用户5s内不可重复提交 + Idempotency-Key 请求级幂等）
     * 金额由服务端根据商品单价×数量计算，下单用户取自当前登录用户
     */
    @PostMapping
    @Operation(summary = "创建订单",
            description = "创建新订单，初始状态为 PENDING。金额由服务端按商品单价×数量计算，不信任前端金额；下单用户取自登录态。已集成 Redisson 分布式锁（同一用户5秒防重复提交）+ Idempotency-Key 请求头幂等（同 key 重复请求返回 1103）",
            security = @SecurityRequirement(name = "Bearer"))
    public Result<Long> createOrder(@Valid @RequestBody OrderCreateRequest request,
                                    @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        Long userId = SecurityUtil.getCurrentUserId();
        // 请求级幂等：同 userId+Idempotency-Key 重复请求被拒绝（Redis SETNX + TTL）
        if (!idempotencyUtil.tryAcquire(userId, idempotencyKey)) {
            throw new BusinessException(ErrorCode.ORDER_CREATE_BUSY);
        }
        return Result.success(orderService.createOrder(request));
    }

    /**
     * GET /api/orders/{id}
     * 根据 ID 查询订单
     */
    @GetMapping("/{id}")
    @Operation(summary = "查询订单详情", description = "根据订单ID查询订单信息", security = @SecurityRequirement(name = "Bearer"))
    @Parameter(name = "id", description = "订单ID", example = "1", required = true)
    public Result<OrderResponse> getOrderById(@PathVariable Long id) {
        return Result.success(orderService.getOrderById(id));
    }

    /**
     * GET /api/orders
     * 分页查询订单列表
     */
    @GetMapping
    @Operation(summary = "分页查询订单列表", description = "支持按用户ID和状态过滤，结果按ID倒序", security = @SecurityRequirement(name = "Bearer"))
    public Result<PageResult<OrderResponse>> queryOrders(@Valid OrderQueryRequest request) {
        return Result.success(orderService.queryOrders(request));
    }

    /**
     * 深分页优化
     * @param request
     * @return
     */
    @GetMapping("/cursor")
    @Operation(summary = "游标分页查询订单列表", description = "深分页优化方案，适用于大数据量场景。非管理员只能查本人订单，管理员可查全部")
    public Result<CursorPageResponse<OrderResponse>> queryOrdersByCursor(
            @Valid CursorPageRequest request) {
        return Result.success(orderService.queryOrdersByCursor(request));
    }


    /**
     * POST /api/orders/{id}/pay
     * 支付订单
     */
    @PostMapping("/{id}/pay")
    @Operation(summary = "支付订单",
            description = "将订单状态从 PENDING 改为 PAID，支付成功后异步触发下游通知",
            security = @SecurityRequirement(name = "Bearer"))
    @Parameter(name = "id", description = "订单ID", example = "1", required = true)
    public Result<Void> payOrder(@PathVariable Long id) {
        orderService.payOrder(id);
        return Result.success();
    }

    /**
     * POST /api/orders/{id}/cancel
     * 取消订单
     */
    @PostMapping("/{id}/cancel")
    @Operation(summary = "取消订单",
            description = "取消订单（COMPLETED状态除外），取消后异步触发库存回滚",
            security = @SecurityRequirement(name = "Bearer"))
    @Parameter(name = "id", description = "订单ID", example = "1", required = true)
    public Result<Void> cancelOrder(@PathVariable Long id) {
        orderService.cancelOrder(id);
        return Result.success();
    }

    /**
     * POST /api/orders/{id}/ship
     * 发货（管理员）：PAID→SHIPPED，触发发货通知
     */
    @PostMapping("/{id}/ship")
    @Operation(summary = "订单发货", description = "管理员发货（PAID→SHIPPED），触发发货通知", security = @SecurityRequirement(name = "Bearer"))
    @Parameter(name = "id", description = "订单ID", example = "1", required = true)
    public Result<Void> shipOrder(@PathVariable Long id) {
        orderService.shipOrder(id);
        return Result.success();
    }

    /**
     * POST /api/orders/{id}/confirm
     * 确认收货（本人）：SHIPPED→COMPLETED
     */
    @PostMapping("/{id}/confirm")
    @Operation(summary = "确认收货", description = "买家确认收货（SHIPPED→COMPLETED），归属校验", security = @SecurityRequirement(name = "Bearer"))
    @Parameter(name = "id", description = "订单ID", example = "1", required = true)
    public Result<Void> confirmReceipt(@PathVariable Long id) {
        orderService.confirmReceipt(id);
        return Result.success();
    }
}
