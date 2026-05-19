package com.aiagent.service.impl;

import com.aiagent.config.AgentConfig;
import com.aiagent.dto.*;
import com.aiagent.service.AgentService;
import com.aiagent.service.llm.LlmClient;
import com.aiagent.service.security.SqlSecurityValidator;
import com.aiagent.service.session.SessionManager;
import com.aiagent.service.tool.ToolExecutor;
import com.aiagent.util.JsonUtil;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Agent核心调度实现
 * <p>
 * 整个项目的核心中枢，实现「思考→行动→观察→总结」的经典Agent闭环。
 * <p>
 * 调度流程：
 * 1. 接收用户自然语言问题
 * 2. 拼接System Prompt + 会话历史，调用大模型
 * 3. 大模型判断是否需要SQL：需要→生成SQL / 不需要→直接回答
 * 4. 校验SQL合法性（三道防线）
 * 5. 执行数据库查询
 * 6. 将「原始问题 + 真实数据」二次传入大模型
 * 7. 大模型基于真实数据生成分析结论
 * 8. 缓存本次对话，返回最终结果
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentServiceImpl implements AgentService {

    private final LlmClient llmClient;
    private final ToolExecutor toolExecutor;
    private final SqlSecurityValidator sqlValidator;
    private final SessionManager sessionManager;
    private final AgentConfig config;

    /**
     * 系统提示词
     * <p>
     * 通过精心设计的Prompt约束大模型输出结构化JSON，
     * 并限定只生成安全的SELECT语句。
     */
    private static final String SYSTEM_PROMPT = """
            你是一个专业的业务数据查询分析助手，能够理解用户的自然语言问题，
            将其转换为MySQL查询语句，并对查询结果进行专业的数据分析。

            ## 可访问的数据库表

            表名：order_info（订单信息表）
            字段说明：
            - id: bigint, 主键ID
            - order_no: varchar(32), 订单编号
            - order_amount: decimal(12,2), 订单金额（元）
            - order_status: tinyint, 订单状态（0=异常, 1=正常）
            - create_time: datetime, 创建时间

            ## 工作规则

            1. 首先判断用户问题是否需要查询数据库：
               - 纯闲聊、问候、功能询问 → 不需要查询
               - 涉及订单数量、金额、统计、趋势、对比 → 需要查询
            2. 生成SQL时，只生成SELECT语句，严禁INSERT/UPDATE/DELETE/DROP等操作
            3. 涉及时间的查询，使用CURDATE()、DATE_SUB()、DATE_FORMAT()等MySQL函数
            4. 金额字段使用DECIMAL类型，比较时不要加引号
            5. 状态字段 order_status 的取值：0=异常, 1=正常

            ## 输出格式

            你必须严格按照以下JSON格式输出，不要包含其他任何文字：

            {
              "needSql": true,
              "reasoning": "你的分析推理过程，简要说明为什么需要/不需要SQL",
              "sql": "生成的SELECT语句",
              "answer": ""
            }

            如果不需要SQL查询：
            {
              "needSql": false,
              "reasoning": "简要推理",
              "sql": "",
              "answer": "你的直接回答内容"
            }

            注意：
            - needSql 为 true 时，sql 字段必须包含有效的SELECT语句
            - needSql 为 false 时，answer 字段必须包含自然语言回答
            - SQL不要包含注释
            - 查询结果限制最多返回500行（使用LIMIT 500）
            - 当前数据库版本为MySQL 8.0
            """;

    /**
     * 数据分析提示词模板
     * <p>
     * 将用户原始问题和查询数据拼接后的二次推理Prompt。
     */
    private static final String ANALYSIS_PROMPT_TEMPLATE = """
            查询结果数据如下（JSON数组格式）：
            %s

            请基于以上真实数据，对用户的原始问题进行专业分析：

            **用户问题**：%s

            **分析要求**：
            1. 用自然、清晰的语言呈现数据结果
            2. 金额数据保留2位小数，并添加千分位格式（如 1,234.56 元）
            3. 数量、计数类型的值使用整数
            4. 如有异常数据（order_status=0的订单），请特别指出并分析可能的原因
            5. 如有趋势性数据（多天/多月对比），请分析变化趋势
            6. 如果查询结果为空，请告知用户"未查询到符合条件的数据"，并给出建议

            请直接输出分析结论，不要包含JSON格式。
            """;

    /**
     * Agent智能问答主入口
     *
     * @param sessionId 会话ID（可为空，后端自动创建）
     * @param question  用户自然语言问题
     * @return AgentResponse 包含分析结论和原始数据
     */
    @Override
    public AgentResponse chat(String sessionId, String question) {
        // ---- Step 0: 会话初始化 ----
        if (sessionId == null || sessionId.isBlank() || !sessionManager.sessionExists(sessionId)) {
            sessionId = sessionManager.createSession();
        }

        log.info("=== Agent调度开始 === sessionId: {}, question: {}", sessionId, question);

        try {
            // ---- Step 1: 拼接消息列表 ----
            List<Message> messages = buildInitialMessages(sessionId, question);

            // ---- Step 2: 第一次大模型调用（决策+SQL生成） ----
            String llmRawResponse = llmClient.chat(messages);
            log.debug("大模型原始返回: {}", llmRawResponse);

            // ---- Step 3: 解析大模型的结构化输出 ----
            LlmResponse llmResponse = parseLlmResponse(llmRawResponse);

            // ---- Step 4: 判断是否需要执行SQL ----
            if (!llmResponse.isNeedSql()) {
                // 不需要SQL，直接返回大模型的回答（闲聊/功能咨询类）
                log.info("大模型判断无需SQL，直接返回回答");
                sessionManager.addExchange(sessionId, question, llmResponse.getAnswer());
                return AgentResponse.success(sessionId, llmResponse.getAnswer(),
                        null, llmResponse.getReasoning(), null);
            }

            // ---- Step 5: SQL安全校验 ----
            String sql = llmResponse.getSql();
            log.info("大模型生成SQL: {}", sql);
            sqlValidator.validate(sql);  // 三道防线校验，不通过直接抛异常

            // ---- Step 6: 执行数据库查询 ----
            List<Map<String, Object>> queryResult = toolExecutor.executeQuery(sql);

            // ---- Step 7: 第二次大模型调用（数据分析+结论生成） ----
            String analysisConclusion = generateAnalysis(question, queryResult);

            // ---- Step 8: 保存会话记录 ----
            sessionManager.addExchange(sessionId, question, analysisConclusion);

            log.info("=== Agent调度完成 === sessionId: {}, 返回数据行数: {}",
                    sessionId, queryResult.size());

            return AgentResponse.success(sessionId, analysisConclusion,
                    queryResult, llmResponse.getReasoning(), sql);

        } catch (IllegalArgumentException e) {
            // 安全校验失败
            log.warn("安全校验拦截: {}", e.getMessage());
            String errorMsg = "查询请求未通过安全检查：" + e.getMessage();
            sessionManager.addExchange(sessionId, question, errorMsg);
            return AgentResponse.fail(4001, errorMsg);

        } catch (Exception e) {
            // 兜底异常处理
            log.error("Agent调度异常", e);
            String errorMsg = "系统处理请求时遇到问题，请稍后重试。错误详情：" + e.getMessage();
            sessionManager.addExchange(sessionId, question, errorMsg);
            return AgentResponse.fail(5000, errorMsg);
        }
    }

    /**
     * 构建初始消息列表：系统提示词 + 会话历史 + 当前问题
     */
    private List<Message> buildInitialMessages(String sessionId, String question) {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.system(SYSTEM_PROMPT));

        // 拼接历史对话（最近 N 轮）
        List<Message> history = sessionManager.getHistory(sessionId);
        messages.addAll(history);

        // 当前问题
        messages.add(Message.user(question));

        return messages;
    }

    /**
     * 解析大模型返回的JSON，提取LlmResponse结构
     * <p>
     * 包含容错处理：大模型可能在JSON前后添加额外的文字，
     * 通过提取第一个 { 到最后一个 } 的子串来兜底。
     */
    private LlmResponse parseLlmResponse(String rawResponse) {
        try {
            // 提取JSON子串（大模型可能在JSON外添加说明文字）
            String jsonStr = JsonUtil.extractJson(rawResponse);
            LlmResponse resp = JSON.parseObject(jsonStr, LlmResponse.class);

            if (!resp.isValid()) {
                throw new RuntimeException("大模型返回的JSON结构不完整: " + jsonStr);
            }

            return resp;
        } catch (Exception e) {
            log.error("解析大模型响应失败，原始内容: {}", rawResponse);
            throw new RuntimeException("大模型返回格式异常，无法解析为结构化指令。请重试。", e);
        }
    }

    /**
     * 第二次大模型调用：基于真实数据生成分析结论
     *
     * @param originalQuestion 用户原始问题
     * @param queryResult      数据库查询结果
     * @return 自然语言分析结论
     */
    private String generateAnalysis(String originalQuestion,
                                     List<Map<String, Object>> queryResult) {
        String dataJson = JSON.toJSONString(queryResult);

        // 数据量较大时做截断保护，避免超出Token限制
        if (dataJson.length() > 8000) {
            dataJson = dataJson.substring(0, 8000) + "...(数据已截断，共"
                    + queryResult.size() + "行)";
        }

        String prompt = String.format(ANALYSIS_PROMPT_TEMPLATE, dataJson, originalQuestion);

        List<Message> messages = List.of(
                Message.system("你是一个专业的数据分析师，能够基于真实业务数据生成清晰的分析报告。"),
                Message.user(prompt)
        );

        return llmClient.chat(messages);
    }
}