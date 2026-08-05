package com.example.project.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 订单商品明细项请求DTO
 */
@Data
@Schema(description = "订单商品明细项")
public class OrderItemRequest {

    @NotNull(message = "商品ID不能为空")
    @Schema(description = "商品ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long productId;

    @NotNull(message = "数量不能为空")
    @Min(value = 1, message = "数量最小为1")
    @Max(value = 9999, message = "单商品数量最大9999")
    @Schema(description = "购买数量", example = "2", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer quantity;
}
