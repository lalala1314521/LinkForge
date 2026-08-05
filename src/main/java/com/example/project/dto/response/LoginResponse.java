package com.example.project.dto.response;

import com.example.project.enums.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 登录成功响应DTO
 */
@Data
@Schema(description = "登录成功响应")
public class LoginResponse {

    @Schema(description = "用户ID", example = "1")
    private Long userId;

    @Schema(description = "用户名", example = "admin")
    private String username;

    @Schema(description = "昵称", example = "管理员")
    private String nickname;

    @Schema(description = "JWT Token, 请求时放入 Authorization: Bearer <token>", example = "eyJhbGciOiJIUzI1Ni9...")
    private String token;

    @Schema(description = "Token有效期（秒）", example = "86400")
    private long expiresIn;

    @Schema(description = "角色: USER/ADMIN", example = "USER")
    private UserRole role;
}