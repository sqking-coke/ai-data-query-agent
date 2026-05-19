package com.aiagent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Agent智能问答响应体
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentResponse {

    /** 响应码：0=成功, 其他=失败 */
    private Integer code;

    /** 响应消息 */
    private String message;

    /** 会话ID */
    private String sessionId;

    /** AI生成的分析结论（自然语言） */
    private String conclusion;

    /** 查询到的原始数据（可选，便于前端展示表格） */
    private List<Map<String, Object>> data;

    /** AI推理过程（可选，便于调试和透明度展示） */
    private String reasoning;

    /** 执行的SQL语句（可选，便于调试） */
    private String executedSql;

    // ========== 便捷工厂方法 ==========

    public static AgentResponse success(String sessionId, String conclusion,
                                         List<Map<String, Object>> data,
                                         String reasoning, String sql) {
        return AgentResponse.builder()
                .code(0)
                .message("success")
                .sessionId(sessionId)
                .conclusion(conclusion)
                .data(data)
                .reasoning(reasoning)
                .executedSql(sql)
                .build();
    }

    public static AgentResponse fail(int code, String message) {
        return AgentResponse.builder()
                .code(code)
                .message(message)
                .build();
    }
}