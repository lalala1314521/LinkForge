package com.example.project.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 秒杀活动响应
 */
@Data
@Schema(description = "秒杀活动响应")
public class SeckillActivityResponse {

    @Schema(description = "活动ID", example = "1")
    private Long id;

    @Schema(description = "活动名称", example = "蓝牙耳机限时秒杀")
    private String name;

    @Schema(description = "关联商品ID", example = "1")
    private Long productId;

    @Schema(description = "秒杀价格", example = "9.90")
    private BigDecimal seckillPrice;

    @Schema(description = "总库存", example = "100")
    private Integer totalStock;

    @Schema(description = "剩余可用库存", example = "80")
    private Integer availableStock;

    @Schema(description = "开始时间")
    private LocalDateTime startTime;

    @Schema(description = "结束时间")
    private LocalDateTime endTime;

    @Schema(description = "状态: CREATED/ACTIVE/ENDED", example = "ACTIVE")
    private String status;
}
