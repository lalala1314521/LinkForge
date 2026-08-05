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

import java.util.List;

/**
 * 智能客服接口（需登录）
 * <p>
 * 多 Provider 路由：support.ai.enabled=true 时注入 OpenAiChatProvider（真实模型，含 RAG 订单上下文），
 * 否则回退 MockChatProvider（规则演示）。填 key 前系统可正常运行（OpenAI 返回"未配置"提示）。
 */
@RestController
@RequestMapping("/api/support")
@RequiredArgsConstructor
@Tag(name = "智能客服", description = "AI 客服对话（需登录，DeepSeek/OpenAI 兼容 + 规则回退）")
public class SupportController {

    private final List<ChatProvider> chatProviders;

    @PostMapping("/chat")
    @Operation(summary = "客服对话", description = "用户输入问题，返回客服回复（真实 AI：RAG 注入订单数据；未配置 key 时回退规则模式）", security = @SecurityRequirement(name = "Bearer"))
    public Result<SupportChatResponse> chat(@Valid @RequestBody SupportChatRequest request) {
        Long userId = SecurityUtil.getCurrentUserId();
        ChatProvider provider = selectProvider();
        String reply = provider.chat(userId, request.getMessage());
        return Result.success(new SupportChatResponse(reply));
    }

    /** 优先 OpenAI 兼容模型（启用时注册），否则回退首个可用（Mock） */
    private ChatProvider selectProvider() {
        return chatProviders.stream()
                .filter(p -> "openai".equals(p.channel()))
                .findFirst()
                .orElseGet(() -> chatProviders.get(0));
    }
}
