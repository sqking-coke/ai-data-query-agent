package com.aiagent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Agent智能问答请求体
 */
@Data
public class AgentRequest {

    /** 会话ID（首次传空，后端自动生成并返回） */
    private String sessionId;

    /** 用户自然语言问题 */
    @NotBlank(message = "问题不能为空")
    private String question;
}