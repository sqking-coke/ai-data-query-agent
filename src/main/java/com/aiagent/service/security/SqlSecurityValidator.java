package com.aiagent.service.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * SQL安全校验器
 * <p>
 * 对AI生成的SQL进行强制安全校验，三道防线层层拦截：
 * <p>
 * 1. 高危操作拦截：禁止 DROP / DELETE / ALTER / TRUNCATE / UPDATE / INSERT
 * 2. SQL注入检测：拦截可疑的关键字和特殊字符组合
 * 3. 表范围限制：只允许查询业务白名单表
 */
@Slf4j
@Component
public class SqlSecurityValidator {

    // === 第一道防线：高危操作关键字（大小写不敏感） ===
    private static final Set<String> DANGEROUS_KEYWORDS = Set.of(
            "DROP", "DELETE", "ALTER", "TRUNCATE", "UPDATE", "INSERT",
            "CREATE", "REPLACE", "RENAME", "GRANT", "REVOKE", "EXEC", "EXECUTE",
            "MERGE", "LOAD", "INTO OUTFILE", "INTO DUMPFILE", "SHUTDOWN"
    );

    // === 第二道防线：SQL注入特征检测 ===
    private static final Pattern[] INJECTION_PATTERNS = {
            Pattern.compile("'.*OR\\s+'1'\\s*=\\s*'1", Pattern.CASE_INSENSITIVE),
            Pattern.compile("'.*OR\\s+1\\s*=\\s*1", Pattern.CASE_INSENSITIVE),
            Pattern.compile("UNION\\s+SELECT", Pattern.CASE_INSENSITIVE),
            Pattern.compile("--\\s*$", Pattern.CASE_INSENSITIVE),   // SQL行注释（作为末尾内容时高危）
            Pattern.compile("/\\*.*\\*/", Pattern.DOTALL),           // 块注释
            Pattern.compile(";\\s*(DROP|DELETE|ALTER|INSERT|UPDATE)", Pattern.CASE_INSENSITIVE),
    };

    // === 第三道防线：允许查询的表白名单 ===
    private static final Set<String> ALLOWED_TABLES = Set.of(
            "order_info"
    );

    /**
     * 对SQL进行三道防线校验
     *
     * @param sql AI生成的原始SQL字符串
     * @throws IllegalArgumentException 校验不通过时抛出，包含具体的拦截原因
     */
    public void validate(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new IllegalArgumentException("SQL不能为空");
        }

        String upperSql = sql.toUpperCase().trim();

        // ----- 第一道：高危操作拦截 -----
        for (String keyword : DANGEROUS_KEYWORDS) {
            // 用正则匹配整个单词，避免误杀字段名中包含的子串
            Pattern p = Pattern.compile("\\b" + keyword + "\\b", Pattern.CASE_INSENSITIVE);
            if (p.matcher(sql).find()) {
                String msg = String.format("安全拦截：SQL中包含高危操作 [%s]，禁止执行。SQL: %s", keyword, sql);
                log.warn(msg);
                throw new IllegalArgumentException(msg);
            }
        }

        // ----- 第二道：SQL注入检测 -----
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(sql).find()) {
                String msg = String.format("安全拦截：SQL包含疑似注入特征，禁止执行。SQL: %s", sql);
                log.warn(msg);
                throw new IllegalArgumentException(msg);
            }
        }

        // ----- 第三道：表白名单校验 -----
        // 从SQL中提取所有 FROM / JOIN 后的表名
        if (!containsAllowedTables(sql)) {
            String msg = String.format("安全拦截：SQL引用了不在白名单中的表。允许的表: %s。SQL: %s",
                    ALLOWED_TABLES, sql);
            log.warn(msg);
            throw new IllegalArgumentException(msg);
        }

        log.info("SQL安全校验通过");
    }

    /**
     * 检查SQL中引用的表是否都在白名单中
     */
    private boolean containsAllowedTables(String sql) {
        // 提取 FROM 和 JOIN 后的标识符
        Pattern tablePattern = Pattern.compile(
                "(?:FROM|JOIN)\\s+`?(\\w+)`?",
                Pattern.CASE_INSENSITIVE
        );
        java.util.regex.Matcher matcher = tablePattern.matcher(sql);
        while (matcher.find()) {
            String tableName = matcher.group(1).toLowerCase();
            if (!ALLOWED_TABLES.contains(tableName)) {
                // 忽略MySQL函数名等误匹配（简单启发式）
                if (isSqlKeyword(tableName)) {
                    continue;
                }
                return false;
            }
        }
        return true;
    }

    /**
     * 简单的SQL关键字判断，避免误杀
     */
    private boolean isSqlKeyword(String word) {
        return Set.of("select", "where", "and", "or", "not", "null", "as",
                "group", "order", "by", "having", "limit", "offset",
                "inner", "left", "right", "outer", "cross", "on",
                "asc", "desc", "is", "in", "like", "between", "exists",
                "case", "when", "then", "else", "end", "distinct",
                "count", "sum", "avg", "max", "min")
                .contains(word);
    }
}