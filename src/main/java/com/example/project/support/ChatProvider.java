package com.example.project.support;

/**
 * AI 客服 Provider 抽象（预留真实大模型接入）
 * <p>
 * 当前内置 MockChatProvider（规则 + 实时查询用户订单数据=轻量 RAG）；
 * 接入真实模型（如 OpenAI 兼容接口）时新增实现并配置，SupportController 无感知。
 */
public interface ChatProvider {

    /** 渠道标识（mock / openai） */
    String channel();

    /** 单轮对话：输入用户消息，返回回复文本 */
    String chat(Long userId, String message);
}
