package com.example.project.dto.response;

import com.example.project.enums.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单响应DTO
 */
@Data
@Schema(description = "订单响应时间")
public class OrderResponse {

    @Schema(description = "订单ID", example = "1")
    private Long id;

    @Schema(description = "订单编号(雪花ID生成)", example = "1847294857123456789")
    private String orderNo;

    @Schema(description = "下单用户ID", example = "1")
    private Long userId;

    @Schema(description = "订单金额", example = "99.99")
    private BigDecimal totalAmount;

    @Schema(description = "使用的用户优惠券ID", example = "1")
    private Long couponId;

    @Schema(description = "优惠券抵扣金额", example = "0.00")
    private BigDecimal couponDiscount;

    @Schema(description = "实付金额", example = "99.99")
    private BigDecimal finalAmount;

    @Schema(description = "订单状态： PENDING-待支付，SHIPPED-已发货，COMPLETED-已完成， CANCELLED-已取消", example = "PENDING")
    private OrderStatus status;

    @Schema(description = "备注", example = "请尽快发货")
    private String remark;

    @Schema(description = "创建时间", example = "2024-01-01T10:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "最后更新时间", example = "2024-01-01T10:00:00")
    private LocalDateTime updatedAt;

}