package com.aiagent.service;

import com.aiagent.dto.AgentResponse;

/**
 * Agent服务接口
 */
public interface AgentService {

    /**
     * Agent智能问答
     *
     * @param sessionId 会话ID（可空，后端自动创建）
     * @param question  用户自然语言问题
     * @return 包含分析结论和原始数据的响应
     */
    AgentResponse chat(String sessionId, String question);
}