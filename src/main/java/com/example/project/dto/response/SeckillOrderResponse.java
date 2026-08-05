package com.example.project.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 我的秒杀订单响应
 * <p>
 * 在 SeckillResult 基础上补充活动名/商品名（PRD R2 验收②要求"我的秒杀"含活动名），
 * 由 SeckillServiceImpl.getMyOrders() 组装。
 */
@Data
@Schema(description = "我的秒杀订单")
public class SeckillOrderResponse {

    @Schema(description = "秒杀订单号", example = "SK1234567890")
    private String orderNo;

    @Schema(description = "秒杀活动ID", example = "1")
    private Long activityId;

    @Schema(description = "秒杀活动名称", example = "蓝牙耳机限时秒杀")
    private String activityName;

    @Schema(description = "商品ID", example = "1")
    private Long productId;

    @Schema(description = "商品名称", example = "经典蓝牙耳机")
    private String productName;

    @Schema(description = "秒杀价格", example = "9.90")
    private BigDecimal seckillPrice;

    @Schema(description = "状态: PENDING/SUCCESS/FAILED/EXPIRED", example = "PENDING")
    private String status;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;
}
