package com.example.project.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 商品分页查询请求
 */
@Data
@Schema(description = "商品分页查询请求")
public class ProductQueryRequest {

    @Schema(description = "关键字（商品名称模糊匹配）", example = "耳机")
    private String keyword;

    @Schema(description = "状态过滤: ON_SALE/OFF_SALE（可选）", example = "ON_SALE")
    private String status;

    @Min(value = 1, message = "页码最小为1")
    @Schema(description = "页码（默认1）", example = "1")
    private Integer page = 1;

    @Min(value = 1, message = "每页数量最小为1")
    @Max(value = 100, message = "每页数量最大为100")
    @Schema(description = "每页数量（默认10，最大100）", example = "10")
    private Integer size = 10;
}
