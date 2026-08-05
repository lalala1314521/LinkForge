package com.example.project.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 我的优惠券响应（含模板信息）
 * <p>
 * id = user_coupons.id（实例）；couponId = coupons.id（模板）
 */
@Data
@Schema(description = "我的优惠券响应")
public class CouponResponse {

    @Schema(description = "用户优惠券实例ID（user_coupons.id）", example = "1")
    private Long id;

    @Schema(description = "用户ID", example = "1")
    private Long userId;

    @Schema(description = "优惠券模板ID（coupons.id）", example = "1")
    private Long couponId;

    @Schema(description = "优惠券名称", example = "新人满100减10券")
    private String name;

    @Schema(description = "优惠金额", example = "10.00")
    private BigDecimal discount;

    @Schema(description = "最低消费金额", example = "100.00")
    private BigDecimal minAmount;

    @Schema(description = "过期时间", example = "2026-09-04T00:00:00")
    private LocalDateTime expireAt;

    @Schema(description = "占用订单号", example = "1847294857123456789")
    private String orderNo;

    @Schema(description = "状态: UNUSED/FROZEN/USED/EXPIRED", example = "UNUSED")
    private String status;

    @Schema(description = "冻结时间")
    private LocalDateTime frozenAt;

    @Schema(description = "使用时间")
    private LocalDateTime usedAt;

    @Schema(description = "领取时间")
    private LocalDateTime createdAt;
}
