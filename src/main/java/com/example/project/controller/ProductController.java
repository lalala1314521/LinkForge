package com.example.project.controller;

import com.example.project.common.PageResult;
import com.example.project.common.Result;
import com.example.project.annotation.AuditLog;
import com.example.project.dto.request.ProductCreateRequest;
import com.example.project.dto.request.ProductQueryRequest;
import com.example.project.dto.request.ProductUpdateRequest;
import com.example.project.dto.response.ProductResponse;
import com.example.project.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 商品管理接口
 * <p>
 * 写操作（POST/PUT/DELETE）ADMIN-only（SecurityConfig 配置）；
 * GET 登录即可访问。删除 = 逻辑下架（status=OFF_SALE）。
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "商品管理", description = "商品 CRUD 与分页查询（写操作需管理员）")
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @AuditLog(action = "CREATE_PRODUCT", targetType = "PRODUCT")
    @Operation(summary = "创建商品", description = "管理员创建商品", security = @SecurityRequirement(name = "Bearer"))
    public Result<Long> create(@Valid @RequestBody ProductCreateRequest request) {
        return Result.success(productService.create(request));
    }

    @PutMapping("/{id}")
    @AuditLog(action = "UPDATE_PRODUCT", targetType = "PRODUCT")
    @Operation(summary = "更新商品", description = "管理员更新商品（名称/价格/库存/状态）", security = @SecurityRequirement(name = "Bearer"))
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ProductUpdateRequest request) {
        productService.update(id, request);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @AuditLog(action = "DELETE_PRODUCT", targetType = "PRODUCT")
    @Operation(summary = "删除商品", description = "逻辑下架（status=OFF_SALE），不物理删除", security = @SecurityRequirement(name = "Bearer"))
    public Result<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return Result.success();
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询商品详情", security = @SecurityRequirement(name = "Bearer"))
    public Result<ProductResponse> getById(@PathVariable Long id) {
        return Result.success(productService.getById(id));
    }

    @GetMapping
    @Operation(summary = "分页查询商品", description = "支持关键字模糊匹配与状态过滤", security = @SecurityRequirement(name = "Bearer"))
    public Result<PageResult<ProductResponse>> queryPage(@Valid ProductQueryRequest request) {
        return Result.success(productService.queryPage(request));
    }
}
