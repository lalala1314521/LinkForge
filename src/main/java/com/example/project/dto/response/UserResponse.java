package com.example.project.dto.response;

import com.example.project.enums.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户响应DTO
 */
@Data
@Schema(description = "用户信息响应")
public class UserResponse {

    @Schema(description = "用户ID", example = "1")
    private Long id;

    @Schema(description = "用户名", example = "admin")
    private String username;

    @Schema(description = "昵称", example = "管理员")
    private String nickname;

    @Schema(description = "手机号", example = "13812345678")
    private String phone;

    @Schema(description = "邮箱", example = "admin@example.com")
    private String email;

    @Schema(description = "用户状态，ACTIVE-正常， DISABLED-禁用, DELETED-已删除", example = "ACTIVE")
    private UserStatus status;

    @Schema(description = "创建时间", example = "2024-01-01T10:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "最后更新时间", example = "2024-01-01T10:00:00")
    private LocalDateTime updatedAt;
}