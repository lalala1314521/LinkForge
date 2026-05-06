package com.example.project.controller;

/**
 * 用户管理接口(RESTful 风格）
 */

import com.example.project.common.PageResult;
import com.example.project.common.Result;
import com.example.project.dto.request.CursorPageRequest;
import com.example.project.dto.request.UserCreateRequest;
import com.example.project.dto.request.UserQueryRequest;
import com.example.project.dto.request.UserUpdateRequest;
import com.example.project.dto.response.CursorPageResponse;
import com.example.project.dto.response.UserResponse;
import com.example.project.enums.UserStatus;
import com.example.project.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "用户管理", description = "用户注册、查询、更新、删除相关接口")
public class UserController {

    private final UserService userService;

    /**
     * POST /api/users
     * 创建用户（注册、公共接口）
     */
    @PostMapping
    @Operation(summary = "注册用户", description = "公共接口，无需认证，用户名唯一，密码需包含大小写字母和数字")
    public Result<Long> createUser(@Valid @RequestBody UserCreateRequest request) {
        return Result.success(userService.createUser(request));
    }

    /**
     * GET /api/users/{id}
     * 根据ID查询用户（带Redis缓存)
     */
    @GetMapping("/{id}")
    @Operation(summary = "查询用户详情", description = "根据用户ID查询，结果缓存1小时", security = @SecurityRequirement(name = "Bearer"))
    @Parameter(name = "id", description = "用户ID", example = "1", required = true)
    public Result<UserResponse> getUserById(@PathVariable Long id) {
        return Result.success(userService.getUserById(id));
    }

    /**
     * GET /api/users
     * 分页查询用户列表
     */
    @GetMapping
    @Operation(summary = "分页查询用户列表", description = "支持按关键词和状态过滤，结果按ID倒序", security = @SecurityRequirement(name = "Bearer"))
    public Result<PageResult<UserResponse>> queryUsers(@Valid UserQueryRequest request) {
        return Result.success(userService.queryUsers(request));
    }

    /**
     * 深度分页优化
     */
    @GetMapping("/cursor")
    @Operation(summary = "游标分页查询用户列表", description = "深分页优化方案，适用于大数据量场景")
    public Result<CursorPageResponse<UserResponse>> queryUsersByCursor(@Valid CursorPageRequest request) {
        return Result.success(userService.queryUsersByCursor(request));
    }


    /**
     * PUT /api/users/{id}
     * 更新用户基本信息
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新用户信息", description = "更新昵称、手机号、邮箱，同时清除缓存", security = @SecurityRequirement(name = "Bearer"))
    @Parameter(name = "id", description = "用户ID", example = "1", required = true)
    public Result<Void> updateUserInfo(@PathVariable Long id,
                                       @Valid @org.springframework.web.bind.annotation.RequestBody UserUpdateRequest request) {
        userService.updateUserInfo(id, request);
        return Result.success();
    }

    /**
     * PATCH /api/users/{id}/status
     * 更新用户状态
     */
    @PatchMapping("/{id}/status")
    @Operation(summary = "更新用户状态", description = "修改用户状态（ACTIVE/DISABLED），同时清除缓存", security = @SecurityRequirement(name = "Bearer"))
    @Parameter(name = "id", description = "用户ID", example = "1", required = true)
    @Parameter(name = "status", description = "目标状态", example = "DISABLED", required = true)
    public Result<Void> updateStatus(@PathVariable Long id,
                                     @RequestParam UserStatus status) {
        userService.updateUserStatus(id, status);
        return Result.success();
    }

    /**
     * DELETE /api/users/{id}
     * 逻辑删除用户
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除用户", description = "逻辑删除（status='DELETED'），不物理移除数据", security = @SecurityRequirement(name = "Bearer"))
    @Parameter(name = "id", description = "用户ID", example = "1", required = true)
    public Result<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return Result.success();
    }

}
