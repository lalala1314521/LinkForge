package com.example.project.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 创建秒杀活动请求
 */
@Data
@Schema(description = "创建秒杀活动请求")
public class SeckillActivityCreateRequest {

    @NotBlank(message = "活动名称不能为空")
    @Size(max = 100, message = "活动名称最多100个字符")
    @Schema(description = "活动名称", example = "蓝牙耳机限时秒杀", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotNull(message = "商品ID不能为空")
    @Schema(description = "关联商品ID（仅校验存在且ON_SALE，不扣商品库存）", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long productId;

    @NotNull(message = "秒杀价格不能为空")
    @DecimalMin(value = "0.01", message = "秒杀价格必须大于0")
    @Schema(description = "秒杀价格（真实价，杜绝0元单）", example = "9.90", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal seckillPrice;

    @NotNull(message = "秒杀总库存不能为空")
    @Min(value = 1, message = "秒杀总库存至少为1")
    @Schema(description = "秒杀总库存", example = "100", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer totalStock;

    @NotNull(message = "开始时间不能为空")
    @Schema(description = "开始时间", example = "2026-08-06T10:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime startTime;

    @NotNull(message = "结束时间不能为空")
    @Schema(description = "结束时间（必须晚于开始时间）", example = "2026-08-07T10:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime endTime;
}
