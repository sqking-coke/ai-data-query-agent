package com.aiagent.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单条对话消息，用于拼接到大模型请求的 messages 数组中
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    /** 角色：system / user / assistant */
    private String role;

    /** 消息内容 */
    private String content;

    // ========== 便捷工厂方法 ==========

    public static Message system(String content) {
        return new Message("system", content);
    }

    public static Message user(String content) {
        return new Message("user", content);
    }

    public static Message assistant(String content) {
        return new Message("assistant", content);
    }
}