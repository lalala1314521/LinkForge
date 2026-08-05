package com.example.project.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 秒杀活动上下架请求（ADMIN）
 */
@Data
@Schema(description = "秒杀活动上下架请求")
public class SeckillActivityStatusRequest {

    @NotBlank(message = "目标状态不能为空")
    @Schema(description = "目标状态: ACTIVE（上架）/ ENDED（下架）", example = "ACTIVE", requiredMode = Schema.RequiredMode.REQUIRED)
    private String targetStatus;
}
