package com.aiagent.controller;

import com.aiagent.dto.AgentRequest;
import com.aiagent.dto.AgentResponse;
import com.aiagent.service.AgentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * Agent接入层
 * <p>
 * 对外暴露REST接口，接收前端用户提问，调用Agent核心服务，返回智能分析结果。
 * <p>
 * 接口列表：
 * - POST /agent/chat  智能数据问答（核心接口）
 * - GET  /agent/health 健康检查
 */
@Slf4j
@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")  // 开发阶段允许跨域，生产需限制
public class AgentController {

    private final AgentService agentService;

    /**
     * Agent智能数据问答接口
     * <p>
     * 用户输入自然语言问题，Agent自动完成SQL生成→安全校验→数据查询→智能分析的全闭环。
     *
     * @param request 请求体（sessionId可为空，question必填）
     * @return AgentResponse 包含AI分析结论 + 查询数据明细
     */
    @PostMapping("/chat")
    public AgentResponse chat(@Valid @RequestBody AgentRequest request) {
        log.info("收到提问 - sessionId: {}, question: {}",
                request.getSessionId(), request.getQuestion());

        return agentService.chat(request.getSessionId(), request.getQuestion());
    }

    /**
     * 健康检查接口
     */
    @GetMapping("/health")
    public String health() {
        return "AI数据查询Agent运行正常";
    }
}