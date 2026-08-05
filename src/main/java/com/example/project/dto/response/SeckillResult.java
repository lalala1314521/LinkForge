package com.example.project.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 秒杀抢购结果
 * <p>
 * doSeckill 返回：orderNo（PENDING 待支付）+ 活动/商品/价格信息；
 * getSeckillOrderStatus 返回：DB 中订单状态（PENDING/PAID/FAILED）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "秒杀抢购结果")
public class SeckillResult {

    @Schema(description = "秒杀订单号（幂等锚点）", example = "SK1847294857123456789")
    private String orderNo;

    @Schema(description = "秒杀活动ID", example = "1")
    private Long activityId;

    @Schema(description = "商品ID", example = "1")
    private Long productId;

    @Schema(description = "秒杀价格", example = "9.90")
    private BigDecimal seckillPrice;

    @Schema(description = "订单状态: PENDING/PAID/FAILED", example = "PENDING")
    private String status;
}
