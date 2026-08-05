package com.example.project.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 客服对话响应
 */
@Data
@AllArgsConstructor
@Schema(description = "客服对话响应")
public class SupportChatResponse {

    @Schema(description = "客服回复")
    private String reply;
}
