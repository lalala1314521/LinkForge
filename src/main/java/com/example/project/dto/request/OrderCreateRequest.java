package com.example.project.dto.request;

import com.example.project.enums.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 创建订单请求DTO
 */
@Data
@Schema(description = "创建订单请求")
public class OrderCreateRequest {

    @NotNull(message = "用户ID不能为空")
    @Schema(description = "下单用户ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long userId;

    @NotNull(message = "订单金额不能为空")
    @DecimalMin(value = "0.01", message = "订单金额最小为0，01")
    @Schema(description = "订单金额最小0.01元", example = "99.99", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal totalAmount;

    @Size(max = 500, message = "备注最多500个字符")
    @Schema(description = "订单备注（可选）", example = "请尽快发货")
    private String remark;
}

