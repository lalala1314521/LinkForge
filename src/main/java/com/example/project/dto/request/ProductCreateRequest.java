package com.example.project.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 创建商品请求
 */
@Data
@Schema(description = "创建商品请求")
public class ProductCreateRequest {

    @NotBlank(message = "商品名称不能为空")
    @Size(max = 100, message = "商品名称最多100个字符")
    @Schema(description = "商品名称", example = "经典蓝牙耳机", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotNull(message = "价格不能为空")
    @DecimalMin(value = "0.01", message = "价格必须大于0")
    @Schema(description = "商品价格", example = "199.00", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal price;

    @NotNull(message = "库存不能为空")
    @Min(value = 0, message = "库存不能为负")
    @Schema(description = "库存", example = "100", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer stock;

    @Schema(description = "状态: ON_SALE/OFF_SALE（默认ON_SALE）", example = "ON_SALE")
    private String status;
}
