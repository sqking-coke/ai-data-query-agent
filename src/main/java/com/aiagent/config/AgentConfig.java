package com.aiagent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * LLM配置属性
 * <p>
 * 从 application.yml 的 llm 节点读取，支持动态切换模型和API地址。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "llm")
public class AgentConfig {

    /** 大模型API地址（OpenAI兼容格式） */
    private String apiUrl;

    /** API密钥 */
    private String apiKey;

    /** 模型名称 */
    private String model;

    /** 最大输出Token数 */
    private Integer maxTokens = 2048;

    /** 温度参数 */
    private Double temperature = 0.1;

    /** 请求超时（毫秒） */
    private Integer timeout = 60000;

    /** 最大重试次数 */
    private Integer maxRetries = 2;
}