package com.example.project.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品响应
 */
@Data
@Schema(description = "商品响应")
public class ProductResponse {

    @Schema(description = "商品ID", example = "1")
    private Long id;

    @Schema(description = "商品名称", example = "经典蓝牙耳机")
    private String name;

    @Schema(description = "商品价格", example = "199.00")
    private BigDecimal price;

    @Schema(description = "库存", example = "100")
    private Integer stock;

    @Schema(description = "状态: ON_SALE/OFF_SALE", example = "ON_SALE")
    private String status;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
