package com.example.project.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 创建订单请求DTO
 * <p>
 * 金额由服务端根据商品单价与数量计算，请求体不信任前端金额；
 * 下单用户由 SecurityContext 中的当前登录用户决定，请求体不接收 userId。
 */
@Data
@Schema(description = "创建订单请求")
public class OrderCreateRequest {

    @NotEmpty(message = "商品明细不能为空")
    @Size(max = 50, message = "单笔订单最多50种商品")
    @Valid
    @Schema(description = "商品明细列表（1-50种商品）", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<OrderItemRequest> items;

    @Size(max = 500, message = "备注最多500个字符")
    @Schema(description = "订单备注（可选）", example = "请尽快发货")
    private String remark;

    @Schema(description = "使用的用户优惠券ID（user_coupons.id 实例；批次1：下单冻结+服务端抵扣）", example = "1")
    private Long couponId;
}
