package com.aiagent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.aiagent.config.AgentConfig;

/**
 * AI数据查询Agent - SpringBoot启动类
 */
@SpringBootApplication
@MapperScan("com.aiagent.mapper")
@EnableConfigurationProperties(AgentConfig.class)
public class AiAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiAgentApplication.class, args);
        System.out.println("========================================");
        System.out.println("  AI数据查询Agent 启动成功！");
        System.out.println("  接口地址: http://localhost:8088/agent/chat");
        System.out.println("========================================");
    }
}