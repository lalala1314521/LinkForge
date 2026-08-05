package com.example.project.controller;

import com.example.project.common.Result;
import com.example.project.dto.request.SupportChatRequest;
import com.example.project.dto.response.SupportChatResponse;
import com.example.project.security.SecurityUtil;
import com.example.project.support.ChatProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 智能客服接口（需登录）
 * <p>
 * ChatProvider 当前为 Mock（规则+实时订单数据=轻量 RAG），
 * 接入真实大模型后替换 Provider 实现即可。
 */
@RestController
@RequestMapping("/api/support")
@RequiredArgsConstructor
@Tag(name = "智能客服", description = "AI 客服对话（需登录，演示模式）")
public class SupportController {

    private final ChatProvider chatProvider;

    @PostMapping("/chat")
    @Operation(summary = "客服对话", description = "用户输入问题，返回客服回复（规则演示，支持订单/支付/发货/退款/积分/券查询）", security = @SecurityRequirement(name = "Bearer"))
    public Result<SupportChatResponse> chat(@Valid @RequestBody SupportChatRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        String reply = chatProvider.chat(userId, request.getMessage());
        return Result.success(new SupportChatResponse(reply));
    }
}
