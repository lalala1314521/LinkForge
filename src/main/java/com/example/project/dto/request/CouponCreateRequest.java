package com.example.project.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 管理员创建优惠券模板请求
 */
@Data
@Schema(description = "创建优惠券模板请求")
public class CouponCreateRequest {

    @NotBlank(message = "优惠券名称不能为空")
    @Size(max = 100, message = "优惠券名称最多100个字符")
    @Schema(description = "优惠券名称", example = "新人满100减10券", requiredMode = Schema.RequiredMode.REQUIRED)
    private String name;

    @NotNull(message = "优惠金额不能为空")
    @DecimalMin(value = "0.01", message = "优惠金额必须大于0")
    @Schema(description = "优惠金额", example = "10.00", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal discount;

    @DecimalMin(value = "0.00", message = "最低消费金额不能为负")
    @Schema(description = "最低消费金额（默认0）", example = "100.00")
    private BigDecimal minAmount;

    @NotNull(message = "发放总量不能为空")
    @Min(value = 1, message = "发放总量至少为1")
    @Schema(description = "发放总量", example = "1000", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer totalCount;

    @NotNull(message = "过期时间不能为空")
    @Future(message = "过期时间必须晚于当前时间")
    @Schema(description = "过期时间", example = "2026-09-04T00:00:00", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime expireAt;
}
