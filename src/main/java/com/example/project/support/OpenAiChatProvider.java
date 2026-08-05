package com.example.project.support;

import com.example.project.common.PageResult;
import com.example.project.dto.request.OrderQueryRequest;
import com.example.project.dto.response.OrderResponse;
import com.example.project.service.OrderService;
import com.example.project.service.PointsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * DeepSeek / OpenAI 兼容大模型客服 Provider（真实 AI 接入）
 * <p>
 * 启用条件：application.yml 中 support.ai.enabled=true（默认 true），
 * api-key 填写 DeepSeek Key 后重启即生效；key 为空时返回提示（不报错）。
 * <p>
 * RAG：实时查询当前用户订单/积分数据，作为 system prompt 上下文注入，
 * 约束模型只依据上下文回答（防幻觉）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "support.ai", name = "enabled", havingValue = "true")
public class OpenAiChatProvider implements ChatProvider {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final OrderService orderService;
    private final PointsService pointsService;

    @Value("${support.ai.base-url:https://api.deepseek.com/v1}")
    private String baseUrl;

    @Value("${support.ai.api-key:}")
    private String apiKey;

    @Value("${support.ai.model:deepseek-chat}")
    private String model;

    @Override
    public String channel() {
        return "openai";
    }

    @Override
    public String chat(Long userId, String message) {
        if (apiKey.isBlank()) {
            return "AI 客服未配置 API Key：请在 application.yml 的 support.ai.api-key 填入 DeepSeek Key 后重启应用。";
        }
        try {
            String context = buildUserContext(userId);
            String reply = callDeepSeek(message, context);
            log.info("[AI客服] userId={} 调用成功，回复长度={}", userId, reply.length());
            return reply;
        } catch (Exception e) {
            log.error("[AI客服] 调用失败：userId={}, error={}", userId, e.getMessage());
            return "AI 客服暂时不可用，请稍后再试。";
        }
    }

    /**
     * 构建用户业务上下文（轻量 RAG）：最近订单 + 积分余额
     */
    private String buildUserContext(Long userId) {
        StringBuilder sb = new StringBuilder();
        try {
            OrderQueryRequest query = new OrderQueryRequest();
            query.setUserId(userId);
            query.setPage(1);
            query.setSize(10);
            PageResult<OrderResponse> orders = orderService.queryOrders(query);
            if (orders.getRecords().isEmpty()) {
                sb.append("用户暂无订单。\n");
            } else {
                sb.append("用户最近订单（最多10条）：\n");
                orders.getRecords().forEach(o -> sb.append("- 订单号 ")
                        .append(o.getOrderNo())
                        .append("，金额 ").append(o.getFinalAmount()).append(" 元")
                        .append("，状态 ").append(o.getStatus()).append("\n"));
            }
        } catch (Exception e) {
            sb.append("（订单数据查询失败）\n");
        }
        try {
            int balance = pointsService.getBalance(userId);
            sb.append("用户积分余额：").append(balance).append(" 分。\n");
        } catch (Exception e) {
            sb.append("（积分数据查询失败）\n");
        }
        return sb.toString();
    }

    private String callDeepSeek(String userMessage, String context) throws Exception {
        String systemPrompt = """
                你是 LinkForge 电商平台的智能客服。请根据【用户业务上下文】回答用户问题。
                规则：
                1. 只依据上下文回答，上下文没有的信息要明确说"我查不到"，绝不编造；
                2. 回答简洁、友好、使用简体中文；
                3. 涉及订单状态时用中文说明（如 待支付/已支付/已发货/已完成/已取消）。

                【用户业务上下文】
                %s
                """.formatted(context);

        String requestBody = """
                {"model":"%s","messages":[{"role":"system","content":%s},{"role":"user","content":%s}],"temperature":0.3}
                """.formatted(model, toJsonString(systemPrompt), toJsonString(userMessage));

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            log.warn("[AI客服] 上游返回 {}：{}", response.statusCode(), response.body());
            throw new RuntimeException("AI 服务返回异常：" + response.statusCode());
        }
        JsonNode root = JSON.readTree(response.body());
        String content = root.path("choices").path(0).path("message").path("content").asText("");
        return content.isBlank() ? "抱歉，AI 未返回有效内容。" : content.trim();
    }

    /** 字符串转 JSON 字符串字面量（含引号与转义） */
    private String toJsonString(String s) throws Exception {
        return JSON.writeValueAsString(s);
    }
}
