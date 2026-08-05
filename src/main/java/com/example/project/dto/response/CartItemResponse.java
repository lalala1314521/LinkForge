package com.example.project.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 购物车条目响应（JOIN 商品信息，小计由服务端计算）
 */
@Data
@Schema(description = "购物车条目")
public class CartItemResponse {

    @Schema(description = "购物车条目ID")
    private Long id;

    @Schema(description = "商品ID")
    private Long productId;

    @Schema(description = "商品名称")
    private String productName;

    @Schema(description = "商品图片URL")
    private String imageUrl;

    @Schema(description = "商品单价")
    private BigDecimal price;

    @Schema(description = "购买数量")
    private Integer quantity;

    @Schema(description = "小计 = 单价 × 数量")
    private BigDecimal subtotal;
}
