package com.aiagent.service.tool;

import com.aiagent.mapper.OrderInfoMapper;
import com.aiagent.service.llm.LlmClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 工具执行器
 * <p>
 * 封装所有可被Agent调用的后端工具能力，统一入口、统一异常处理。
 * <p>
 * 内置工具：
 * - 数据库查询工具：执行安全校验后的SELECT语句
 * <p>
 * 扩展方式：新增方法 + 在AgentServiceImpl的ToolType枚举中添加对应项即可，
 * 无需修改核心调度逻辑。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolExecutor {

    private final OrderInfoMapper orderInfoMapper;

    /**
     * 执行数据库查询
     * <p>
     * 安全前提：sql参数已通过 SqlSecurityValidator 校验，
     * 本方法不再重复校验，直接执行。
     *
     * @param sql 已验证的SELECT语句
     * @return 查询结果
     */
    public List<Map<String, Object>> executeQuery(String sql) {
        long start = System.currentTimeMillis();
        log.info("执行SQL查询: {}", sql);
        List<Map<String, Object>> result = orderInfoMapper.executeRawSql(sql);
        long elapsed = System.currentTimeMillis() - start;
        log.info("查询完成，返回 {} 行数据，耗时 {}ms", result.size(), elapsed);
        return result;
    }
}