package com.example.project.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 秒杀活动分页查询请求（管理端）
 */
@Data
@Schema(description = "秒杀活动分页查询请求")
public class SeckillActivityQueryRequest {

    @Schema(description = "状态过滤: CREATED/ACTIVE/ENDED（可选）", example = "ACTIVE")
    private String status;

    @Min(value = 1, message = "页码最小为1")
    @Schema(description = "页码（默认1）", example = "1")
    private Integer page = 1;

    @Min(value = 1, message = "每页数量最小为1")
    @Max(value = 100, message = "每页数量最大为100")
    @Schema(description = "每页数量（默认10，最大100）", example = "10")
    private Integer size = 10;
}
