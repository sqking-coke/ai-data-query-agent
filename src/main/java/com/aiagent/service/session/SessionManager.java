package com.aiagent.service.session;

import com.aiagent.dto.Message;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话记忆管理器
 * <p>
 * 基于 ConcurrentHashMap 实现轻量级多轮对话存储。
 * <p>
 * 核心能力：
 * - 按 sessionId 隔离不同用户的对话上下文
 * - 自动限制每个会话的历史记录条数，防止Token溢出
 * - 后续可无缝替换为 Redis 实现分布式会话
 */
@Slf4j
@Component
public class SessionManager {

    /** 每个会话最多保留的对话轮次 */
    private static final int MAX_HISTORY_TURNS = 10;

    /**
     * 会话存储：sessionId → 该会话的对话历史列表
     * <p>
     * ConcurrentHashMap 保证并发安全，不需要额外加锁。
     * 后续升级Redis时替换此Map即可，接口不变。
     */
    private final Map<String, List<Exchange>> sessions = new ConcurrentHashMap<>();

    /**
     * 单轮对话记录
     */
    @Data
    public static class Exchange {
        private String question;
        private String answer;
        private LocalDateTime timestamp;

        public Exchange(String question, String answer) {
            this.question = question;
            this.answer = answer;
            this.timestamp = LocalDateTime.now();
        }
    }

    /**
     * 生成新的会话ID
     */
    public String createSession() {
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        sessions.put(sessionId, new ArrayList<>());
        log.info("创建会话: {}", sessionId);
        return sessionId;
    }

    /**
     * 保存一轮对话
     *
     * @param sessionId 会话ID
     * @param question  用户问题
     * @param answer    AI回答
     */
    public void addExchange(String sessionId, String question, String answer) {
        List<Exchange> history = sessions.computeIfAbsent(sessionId, k -> new ArrayList<>());
        history.add(new Exchange(question, answer));

        // 超出上限时移除最早的记录
        while (history.size() > MAX_HISTORY_TURNS) {
            history.remove(0);
        }
        log.debug("会话[{}]新增一轮对话，当前历史: {} 轮", sessionId, history.size());
    }

    /**
     * 获取会话的对话历史，转换为LLM所需的Message列表
     * <p>
     * 返回最近 N 轮的 user + assistant 交替消息。
     *
     * @param sessionId 会话ID
     * @return 可用于拼接到LLM messages列表中的历史消息
     */
    public List<Message> getHistory(String sessionId) {
        List<Exchange> history = sessions.get(sessionId);
        if (history == null || history.isEmpty()) {
            return Collections.emptyList();
        }

        List<Message> messages = new ArrayList<>();
        for (Exchange ex : history) {
            messages.add(Message.user(ex.getQuestion()));
            messages.add(Message.assistant(ex.getAnswer()));
        }
        return messages;
    }

    /**
     * 检查会话是否存在
     */
    public boolean sessionExists(String sessionId) {
        return sessionId != null && sessions.containsKey(sessionId);
    }

    /**
     * 清除超时会话（可配合定时任务调用）
     * <p>
     * 当前为内存存储，定期清理可防止内存泄漏。
     * 升级Redis后可利用TTL自动过期。
     */
    public void cleanExpiredSessions(long maxIdleMinutes) {
        // 简化实现：不做超时检测，只保留最近MAX_HISTORY_TURNS条
        // 实际生产环境建议迁移至Redis并利用TTL
        log.debug("当前活跃会话数: {}", sessions.size());
    }
}