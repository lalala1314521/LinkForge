package com.example.project.dto.request;

import com.example.project.enums.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import lombok.Data;

/**
 * 订单分页查询清求DTO
 */
@Data
@Schema(description = "订单分页查询清求")
public class OrderQueryRequest {

    @Schema(description = "按用户ID过滤", example = "1")
    private Long userId;

    @Schema(description = "按订单状态过滤", example = "PENDING")
    private OrderStatus status;

    @Min(value = 1, message = "页码最小为1")
    @Schema(description = "页码从1开始", example = "1", defaultValue = "1")
    private int page = 1;

    @Min(value = 1, message = "每页最少1条")
    @Max(value = 100, message = "每页最多100条")
    @Schema(description = "每页大小1-100", example = "20", defaultValue = "20")
    private int size = 20;
}