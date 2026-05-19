package com.aiagent.dto;

import lombok.Data;

/**
 * 大模型返回的结构化JSON对应的DTO
 * <p>
 * 大模型被Prompt约束，必须返回如下JSON结构：
 * <pre>
 * {
 *   "needSql": true/false,
 *   "reasoning": "AI推理过程...",
 *   "sql": "SELECT ...",        // needSql=true时必填
 *   "answer": "直接回答的内容"   // needSql=false时必填
 * }
 * </pre>
 */
@Data
public class LlmResponse {

    /** 是否需要生成并执行SQL */
    private Boolean needSql;

    /** AI的推理思考过程 */
    private String reasoning;

    /** AI生成的SQL查询语句（needSql=true时非空） */
    private String sql;

    /** AI直接回答的内容（needSql=false时非空） */
    private String answer;

    /**
     * 判断此次响应是否有效
     */
    public boolean isValid() {
        if (needSql == null) return false;
        if (needSql) {
            return sql != null && !sql.isBlank();
        } else {
            return answer != null && !answer.isBlank();
        }
    }

    /**
     * 判断是否需要执行sql
     */
    public boolean isNeedSql() {
        if (needSql == null){
            return false;
        }
        return true;
    }
}