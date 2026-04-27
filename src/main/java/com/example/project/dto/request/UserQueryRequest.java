package com.example.project.dto.request;

import com.example.project.enums.UserStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 用户分页查询请求 DTO
 */
@Data
@Schema(description = "用户分页查询请求")
public class UserQueryRequest {

    @Schema(description = "关键词（模糊匹配用户名称或昵称", example = "admin")
    private String keyword;

    @Schema(description = "用户状态过滤", example = "ACTIVE")
    private UserStatus status;

    @Min(value = 1, message = "页码最小为1")
    private int page = 1;

    @Min(value = 1, message = "每页最少1条")
    @Max(value = 100, message = "每页最多100条")
    @Schema(description = "每页大小（1-100）", example = "20", defaultValue = "20")
    private int size = 20;
}