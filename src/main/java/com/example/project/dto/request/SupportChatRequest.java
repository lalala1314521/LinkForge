package com.example.project.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 客服对话请求
 */
@Data
@Schema(description = "客服对话请求")
public class SupportChatRequest {

    @NotBlank(message = "消息不能为空")
    @Size(max = 500, message = "消息最长500字")
    @Schema(description = "用户消息", example = "我的订单状态", requiredMode = Schema.RequiredMode.REQUIRED)
    private String message;
}
