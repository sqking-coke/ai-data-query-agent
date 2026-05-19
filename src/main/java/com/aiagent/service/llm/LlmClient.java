package com.aiagent.service.llm;

import com.aiagent.config.AgentConfig;
import com.aiagent.dto.Message;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 大模型HTTP客户端
 * <p>
 * 统一封装 OpenAI 兼容格式的 Chat Completions API 调用，
 * 支持通义千问、DeepSeek、讯飞星火、本地Ollama等兼容接口。
 * <p>
 * 核心能力：统一调用入口、结构化输出约束、超时重试、异常兜底。
 */
@Slf4j
@Service
public class LlmClient {

    private final OkHttpClient httpClient;
    private final AgentConfig config;

    public LlmClient(@Qualifier("agentConfig") AgentConfig agentConfig) {
        this.config = agentConfig;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(config.getTimeout(), TimeUnit.MILLISECONDS)
                .readTimeout(config.getTimeout(), TimeUnit.MILLISECONDS)
                .writeTimeout(config.getTimeout(), TimeUnit.MILLISECONDS)
                .build();
    }

    /**
     * 调用大模型进行对话
     *
     * @param messages 消息列表（包含system/user/assistant角色）
     * @return 大模型返回的文本内容
     */
    public String chat(List<Message> messages) {
        return chatWithRetry(messages, 0);
    }

    /**
     * 带重试的大模型调用
     */
    private String chatWithRetry(List<Message> messages, int attempt) {
        try {
            return doChat(messages);
        } catch (IOException e) {
            log.warn("大模型调用失败（第{}次尝试）: {}", attempt + 1, e.getMessage());
            if (attempt < config.getMaxRetries()) {
                return chatWithRetry(messages, attempt + 1);
            }
            throw new RuntimeException("大模型调用失败，已重试" + config.getMaxRetries() + "次", e);
        }
    }

    /**
     * 执行一次大模型HTTP调用
     */
    private String doChat(List<Message> messages) throws IOException {
        // 构建请求体
        JSONObject body = new JSONObject();
        body.put("model", config.getModel());
        body.put("max_tokens", config.getMaxTokens());
        body.put("temperature", config.getTemperature());

        JSONArray msgs = new JSONArray();
        for (Message m : messages) {
            JSONObject msg = new JSONObject();
            msg.put("role", m.getRole());
            msg.put("content", m.getContent());
            msgs.add(msg);
        }
        body.put("messages", msgs);

        // 构建HTTP请求
        Request request = new Request.Builder()
                .url(config.getApiUrl())
                .addHeader("Authorization", "Bearer " + config.getApiKey())
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(body.toJSONString(),
                        MediaType.parse("application/json")))
                .build();

        log.debug("大模型请求 - model: {}, 消息数: {}", config.getModel(), messages.size());

        // 执行请求
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                throw new IOException("HTTP " + response.code() + ": " + errorBody);
            }

            String responseBody = response.body().string();
            return parseResponseContent(responseBody);
        }
    }

    /**
     * 从OpenAI格式的响应中提取 content 文本
     */
    private String parseResponseContent(String responseBody) {
        JSONObject resp = JSON.parseObject(responseBody);
        JSONArray choices = resp.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new RuntimeException("大模型返回空choices: " + responseBody);
        }
        JSONObject firstChoice = choices.getJSONObject(0);
        JSONObject message = firstChoice.getJSONObject("message");
        if (message == null) {
            throw new RuntimeException("大模型返回空message: " + responseBody);
        }
        String content = message.getString("content");
        if (content == null || content.isBlank()) {
            throw new RuntimeException("大模型返回空content: " + responseBody);
        }
        log.debug("大模型响应内容长度: {} 字符", content.length());
        return content;
    }
}