package com.example.project.controller;

/**
 * 认证接口（登录/注册）
 */

import com.example.project.common.Result;
import com.example.project.dto.request.LoginRequest;
import com.example.project.dto.response.LoginResponse;
import com.example.project.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "认证管理", description = "用户登录， Token管理相关接口")
public class AuthController {
    private final UserService userService;

    /**
     * POST /api/auth/login
     * 用户登录，返回 JWT Token
     */
    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "验证用户名密码，成功后返回 JWT Token 24小时有效")
    public Result<LoginResponse> login(@Valid@RequestBody LoginRequest request) {
        return Result.success(userService.login(request));
    }

}