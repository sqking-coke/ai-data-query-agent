# 业务数据智能查询 Agent — 学习指南

> **项目地址**：https://github.com/sqking-coke/ai-data-query-agent <br>
> **适用对象**：Java 后端开发者，具备 Spring Boot 基础，想深入学习 AI Agent 架构设计

---

## 目录

1. [项目概览与学习目标](#1-项目概览与学习目标)
2. [AI Agent 核心原理](#2-ai-agent-核心原理)
3. [项目架构全景图](#3-项目架构全景图)
4. [启动类与配置层详解](#4-启动类与配置层详解)
5. [接入层 — Controller](#5-接入层--controller)
6. [DTO 层 — 数据穿梭的契约](#6-dto-层--数据穿梭的契约)
7. [大模型客户端 — LlmClient](#7-大模型客户端--llmclient)
8. [SQL 安全校验 — 三道防线](#8-sql-安全校验--三道防线)
9. [工具执行器 — ToolExecutor](#9-工具执行器--toolexecutor)
10. [会话记忆管理 — SessionManager](#10-会话记忆管理--sessionmanager)
11. [Agent 调度核心 — AgentServiceImpl](#11-agent-调度核心--agentserviceimpl)
12. [数据层 — Entity & Mapper](#12-数据层--entity--mapper)
13. [完整请求链路时序图](#13-完整请求链路时序图)
14. [如何扩展项目](#14-如何扩展项目)
15. [教学重点 FAQ](#15-教学重点-faq)

---

## 1. 项目概览与学习目标

### 1.1 项目是什么

一个**纯 Java 原生实现**的 AI Agent 应用。用户用自然语言提问（如"本月订单总金额是多少？异常订单有几笔？"），系统自动完成：

```
自然语言提问 → 大模型生成 SQL → 安全校验 → 数据库查询 → 大模型分析数据 → 返回结论
```

整个链路不依赖 LangChain4j、Spring AI 等重型 Agent 框架，**Agent 调度核心全部手写**，约 1200 行 Java 代码即可完整运行。

### 1.2 能学到什么

| 维度 | 具体知识点 |
|---|---|
| **AI Agent 原理** | 思考-行动-观察-总结 闭环、工具调用（Tool Calling）的手动实现 |
| **Prompt 工程** | 如何设计 System Prompt 约束大模型输出结构化 JSON |
| **安全设计** | SQL 注入防护的三道防线模型、白名单机制 |
| **会话管理** | 多轮对话上下文拼接策略、上下文窗口管理 |
| **工程架构** | Controller → Service → 工具层的分层设计思路 |
| **HTTP 客户端** | OkHttp 的同步调用、超时重试、OpenAI 兼容 API 协议 |
| **容错设计** | 大模型输出不稳定的 JSON 提取容错、异常兜底策略 |

### 1.3 前置知识

- Java 17 基础语法、SpringBoot 基础（IoC / DI / 配置绑定）
- MyBatis-Plus 基础用法
- MySQL 基本 SQL 语句
- 使用过任何一个大模型对话产品（如 ChatGPT、DeepSeek）

---

## 2. AI Agent 核心原理

### 2.1 什么是 Agent

普通的大模型调用是"一问一答"：你问、模型答，没有外部行动能力。

**Agent = 大模型 + 工具调用 + 调度循环**

```
┌──────────────────────────────────────────────┐
│                 Agent 调度核心                  │
│                                              │
│   ┌──────────┐    ┌──────────┐    ┌────────┐ │
│   │ 思考(Think) │ → │ 行动(Act) │ → │观察(Obs)│ │
│   │ 大模型判断   │    │ 执行工具   │    │拿结果   │ │
│   │ 要什么工具   │    │          │    │        │ │
│   └──────────┘    └──────────┘    └────────┘ │
│         ↑                              │      │
│         └──────── 总结(Answer) ←──────┘      │
│                  将结果+问题再次              │
│                  交给大模型分析               │
└──────────────────────────────────────────────┘
```

本项目简化为 **两段式调用**：

1. **第一次调用（决策 + SQL 生成）**：大模型读 System Prompt + 用户问题 → 判断是否需要 SQL → 输出 SQL
2. **执行工具**：Java 后端执行 SQL（安全校验后）
3. **第二次调用（数据分析）**：把查询到的数据 + 用户问题再次给大模型 → 生成人类可读的分析结论

### 2.2 为什么不直接用 LangChain4j / Spring AI

这些框架封装了 Agent 循环，3 行代码就能跑，但学习时你**看不到内部发生了什么**。本项目手写全部核心逻辑，让你彻底理解：

- 大模型返回的结构化 JSON 是如何被解析和执行的
- 工具调用的路由决策是如何做的
- 多轮对话的上下文是如何拼接和维护的

**先懂原理，再用框架**，这是正确的学习路径。

---

## 3. 项目架构全景图

### 3.1 目录结构

```
ai-data-query-agent/
├── pom.xml                                  # Maven 依赖配置
├── sql/init.sql                             # 数据库初始化脚本
├── src/main/java/com/aiagent/
│   ├── AiAgentApplication.java              # SpringBoot 启动类
│   ├── config/
│   │   └── AgentConfig.java                 # LLM 配置属性（@ConfigurationProperties）
│   ├── controller/
│   │   └── AgentController.java             # REST 接入层
│   ├── dto/
│   │   ├── AgentRequest.java                # 请求体（sessionId + question）
│   │   ├── AgentResponse.java               # 响应体（conclusion + data + reasoning + sql）
│   │   ├── LlmResponse.java                 # 大模型返回的结构化 JSON 映射
│   │   └── Message.java                     # 通用对话消息（role + content）
│   ├── entity/
│   │   └── OrderInfo.java                   # 订单实体类
│   ├── mapper/
│   │   └── OrderInfoMapper.java             # MyBatis-Plus Mapper（含动态SQL执行）
│   ├── service/
│   │   ├── AgentService.java                # Agent 服务接口
│   │   ├── impl/
│   │   │   └── AgentServiceImpl.java        # ★ Agent 调度核心（最重要）
│   │   ├── llm/
│   │   │   └── LlmClient.java               # 大模型 HTTP 客户端
│   │   ├── tool/
│   │   │   └── ToolExecutor.java            # 工具执行器（数据库查询）
│   │   ├── security/
│   │   │   └── SqlSecurityValidator.java    # SQL 三道防线安全校验
│   │   └── session/
│   │       └── SessionManager.java          # 会话记忆管理器
│   └── util/
│       └── JsonUtil.java                    # JSON 提取工具
└── src/main/resources/
    └── application.yml                      # 应用配置（数据库 + LLM）
```

### 3.2 四层架构

```
┌─────────────────────────────────────────────┐
│  ① 接入层 Controller                         │
│     REST 接口、参数校验、跨域                   │
├─────────────────────────────────────────────┤
│  ② Agent 调度层 Service                       │
│     ★ 核心中枢：决策路由、流程编排、异常兜底     │
├─────────────────────────────────────────────┤
│  ③ 工具能力层 Tool / Security / Session       │
│     SQL 执行、安全校验、会话记忆               │
├─────────────────────────────────────────────┤
│  ④ 基础服务层 LlmClient / MyBatis             │
│     HTTP 调用、数据库访问、重试机制             │
└─────────────────────────────────────────────┘
```

关键设计原则：**上层依赖下层，下层不感知上层**。工具能力层可以被不同的 Agent 调度逻辑复用。

---

## 4. 启动类与配置层详解

### 4.1 启动类 `AiAgentApplication.java`

```java
@SpringBootApplication
@MapperScan("com.aiagent.mapper")          // 扫描 MyBatis Mapper
@EnableConfigurationProperties(AgentConfig.class)  // 启用 @ConfigurationProperties 绑定
public class AiAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiAgentApplication.class, args);
    }
}
```

**三个关键注解**：

| 注解 | 作用 |
|---|---|
| `@SpringBootApplication` | 组合注解：自动配置 + 组件扫描 + 配置类 |
| `@MapperScan` | 扫描 MyBatis-Plus 的 Mapper 接口，生成代理对象 |
| `@EnableConfigurationProperties` | 将 `AgentConfig` 注册为 Bean，使其从 `application.yml` 自动绑定配置 |

### 4.2 配置类 `AgentConfig.java`

```java
@Data
@Component
@ConfigurationProperties(prefix = "llm")   // 绑定 application.yml 中的 llm.* 配置
public class AgentConfig {
    private String apiUrl;       // API 地址
    private String apiKey;       // 密钥
    private String model;        // 模型名
    private Integer maxTokens = 2048;    // 默认值，可被 yml 覆盖
    private Double temperature = 0.1;
    private Integer timeout = 60000;
    private Integer maxRetries = 2;
}
```

**`@ConfigurationProperties(prefix = "llm")` 的工作原理**：

`application.yml` 中的：
```yaml
llm:
  api-url: https://api.deepseek.com/v1/chat/completions
  model: deepseek-v4-flash
```

会被 SpringBoot 自动映射为 `agentConfig.setApiUrl(...)` 和 `agentConfig.setModel(...)`。

**这种方式的好处**：
- 切换模型只需修改 yml，不需要改代码
- 属性有默认值，减少配置量
- 类型安全（`Integer timeout` 而不是字符串）

### 4.3 `application.yml` 关键配置

```yaml
server:
  port: 8088                              # 服务端口

spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ai-data-query-agent?...  # 数据库连接
    username: root
    password: 123456
    hikari:                               # HikariCP 连接池配置
      minimum-idle: 5                     # 最小空闲连接
      maximum-pool-size: 20               # 最大连接数
      connection-timeout: 30000           # 获取连接超时

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true    # 下划线转驼峰：order_no → orderNo
    log-impl: ...StdOutImpl              # SQL 日志输出到控制台

llm:
  api-url: https://api.deepseek.com/v1/chat/completions  # OpenAI 兼容接口
  api-key: sk-xxxxx                      # 密钥（部署时替换为环境变量）
  model: deepseek-v4-flash               # 模型名
  temperature: 0.1                       # 低温度 → 更确定的输出（适合 SQL 生成）
  timeout: 60000                         # 60 秒超时
  max-retries: 2                         # 最多重试 2 次
```

**为什么要设置 `temperature: 0.1`？**

Temperature 控制输出的随机性。SQL 生成需要**确定性**（同样的问法应该生成同样的 SQL），所以设为较低值（0.0~0.3）。设为 0.7+ 会让模型"更有创意"，但 SQL 可能每次都不一样甚至出现幻觉。

---

## 5. 接入层 — Controller

### 5.1 `AgentController.java`

```java
@Slf4j
@RestController
@RequestMapping("/agent")
@RequiredArgsConstructor                   // Lombok：生成带 final 字段的构造器
@CrossOrigin(origins = "*")                // 允许前端跨域调用
public class AgentController {

    private final AgentService agentService;

    @PostMapping("/chat")
    public AgentResponse chat(@Valid @RequestBody AgentRequest request) {
        log.info("收到提问 - sessionId: {}, question: {}",
                request.getSessionId(), request.getQuestion());
        return agentService.chat(request.getSessionId(), request.getQuestion());
    }

    @GetMapping("/health")
    public String health() {
        return "AI数据查询Agent运行正常";
    }
}
```

**设计要点**：

1. **`@Valid` + `@NotBlank`**：在 DTO 字段上用 `@NotBlank(message = "问题不能为空")`，由 Spring Validation 自动校验，不合法请求直接返回 400。
2. **`@RequiredArgsConstructor`**：替代 `@Autowired`，通过构造器注入。Spring 4.3+ 对单构造器自动执行 DI，这也是当前推荐的注入方式。
3. **Controller 极薄**：只做参数接收和日志记录，实际逻辑全部下沉到 Service 层。

### 5.2 可测试性设计

```java
// Controller 不包含业务逻辑，可以这样进行单元测试：
// 1. Mock AgentService
// 2. 调用 controller.chat(request)
// 3. 验证返回值 == mockService 的返回值
```

这是企业开发的核心原则：**Controller 薄、Service 厚、工具类独立可测**。

---

## 6. DTO 层 — 数据穿梭的契约

### 6.1 `AgentRequest` — 请求体

```java
@Data
public class AgentRequest {
    private String sessionId;                      // 会话ID，首次为空
    @NotBlank(message = "问题不能为空")
    private String question;                       // 用户问题
}
```

### 6.2 `AgentResponse` — 响应体

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentResponse {
    private Integer code;                  // 0=成功
    private String message;                // "success" 或错误描述
    private String sessionId;              // 会话ID（前端保存后可继续对话）
    private String conclusion;             // AI 生成的自然语言分析结论
    private List<Map<String, Object>> data; // 原始查询数据（前端可渲染为表格）
    private String reasoning;              // AI 推理过程（便于调试）
    private String executedSql;            // 执行的 SQL（便于调试）

    // 静态工厂方法，避免到处 new/Builder
    public static AgentResponse success(...) { ... }
    public static AgentResponse fail(int code, String message) { ... }
}
```

**设计思路**：

- `data` 用 `List<Map<String, Object>>` 而非强类型 List，因为 AI 生成的 SQL 是动态的，SELECT 的列不固定
- `reasoning` 和 `executedSql` 是可选的，方便开发者调试和前端展示"AI 是怎么想的"
- 静态工厂方法 `success()` 和 `fail()` 让代码更语义化

### 6.3 `LlmResponse` — 大模型返回结构

这是**最关键的数据结构**，它定义了 System Prompt 要求大模型输出的 JSON 格式：

```java
@Data
public class LlmResponse {
    private Boolean needSql;     // true=需要查数据库，false=直接回答
    private String reasoning;    // AI 思考过程
    private String sql;          // needSql=true 时必填
    private String answer;       // needSql=false 时必填

    public boolean isValid() {
        if (needSql == null) return false;
        if (needSql) return sql != null && !sql.isBlank();
        else return answer != null && !answer.isBlank();
    }
}
```

**这是"结构化输出约束"的实现**：System Prompt 中明确告诉大模型必须输出这个格式的 JSON，然后 Java 侧用 FastJSON2 反序列化为这个对象。如果大模型不按格式输出（比如多了一段文字），`extractJson()` 会尝试提取 JSON 子串来容错。

### 6.4 `Message` — 对话消息

```java
@Data
@AllArgsConstructor
public class Message {
    private String role;     // "system" / "user" / "assistant"
    private String content;  // 消息内容

    public static Message system(String content) { return new Message("system", content); }
    public static Message user(String content)    { return new Message("user", content); }
    public static Message assistant(String content) { return new Message("assistant", content); }
}
```

这是一个**通用消息模型**，对应 OpenAI Chat Completions API 的 messages 数组中的每一条。三个静态工厂方法让代码可读性更好：

```java
// 清晰的写法
messages.add(Message.system(SYSTEM_PROMPT));
messages.add(Message.user("本月订单总金额是多少？"));

// 而不是
messages.add(new Message("system", SYSTEM_PROMPT));
```

---

## 7. 大模型客户端 — LlmClient

### 7.1 核心逻辑

```java
@Slf4j
@Service
public class LlmClient {
    private final OkHttpClient httpClient;
    private final AgentConfig config;

    public LlmClient(AgentConfig agentConfig) {
        this.config = agentConfig;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(config.getTimeout(), TimeUnit.MILLISECONDS)
                .readTimeout(config.getTimeout(), TimeUnit.MILLISECONDS)
                .writeTimeout(config.getTimeout(), TimeUnit.MILLISECONDS)
                .build();
    }

    public String chat(List<Message> messages) {
        return chatWithRetry(messages, 0);     // 从第 0 次重试开始
    }
}
```

### 7.2 请求构建

```java
private String doChat(List<Message> messages) throws IOException {
    // 1. 构建 JSON 请求体
    JSONObject body = new JSONObject();
    body.put("model", config.getModel());
    body.put("max_tokens", config.getMaxTokens());
    body.put("temperature", config.getTemperature());

    JSONArray msgs = new JSONArray();
    for (Message m : messages) {
        JSONObject msg = new JSONObject();
        msg.put("role", m.getRole());      // system / user / assistant
        msg.put("content", m.getContent());
        msgs.add(msg);
    }
    body.put("messages", msgs);

    // 2. 构建 OkHttp Request
    Request request = new Request.Builder()
            .url(config.getApiUrl())
            .addHeader("Authorization", "Bearer " + config.getApiKey())
            .addHeader("Content-Type", "application/json")
            .post(RequestBody.create(body.toJSONString(),
                    MediaType.parse("application/json")))
            .build();

    // 3. 执行同步 HTTP 调用
    try (Response response = httpClient.newCall(request).execute()) {
        // 4. 从响应中提取 choices[0].message.content
        ...
    }
}
```

**为什么用 OkHttp 而不是 RestTemplate？**

- OkHttp 是 Square 公司出品的高性能 HTTP 客户端，Android 和 Spring 生态都在用
- 支持 HTTP/2、连接池、自动重连
- 比 `RestTemplate`（已进入维护模式）更现代，比 `WebClient`（响应式）更简单

### 7.3 重试机制

```java
private String chatWithRetry(List<Message> messages, int attempt) {
    try {
        return doChat(messages);
    } catch (IOException e) {
        log.warn("大模型调用失败（第{}次尝试）: {}", attempt + 1, e.getMessage());
        if (attempt < config.getMaxRetries()) {
            return chatWithRetry(messages, attempt + 1);  // 递归重试
        }
        throw new RuntimeException("大模型调用失败，已重试" + config.getMaxRetries() + "次", e);
    }
}
```

**注意**：这里只重试 `IOException`（网络问题），不重试业务错误（如 401 密钥错误、400 参数错误），因为重试非网络类的错误没有意义。

### 7.4 响应解析

```java
private String parseResponseContent(String responseBody) {
    JSONObject resp = JSON.parseObject(responseBody);
    JSONArray choices = resp.getJSONArray("choices");
    // 取 choices[0].message.content
    JSONObject firstChoice = choices.getJSONObject(0);
    JSONObject message = firstChoice.getJSONObject("message");
    return message.getString("content");
}
```

这段代码解析的是 OpenAI Chat Completions API 的标准响应格式：

```json
{
  "choices": [
    {
      "message": {
        "role": "assistant",
        "content": "大模型返回的实际文本内容"
      }
    }
  ]
}
```

---

## 8. SQL 安全校验 — 三道防线

这是项目的**安全性核心模块**。由于 SQL 由 AI 动态生成，不可直接信任，必须多层校验。

### 8.1 第一道防线：高危操作关键字拦截

```java
private static final Set<String> DANGEROUS_KEYWORDS = Set.of(
    "DROP", "DELETE", "ALTER", "TRUNCATE", "UPDATE", "INSERT",
    "CREATE", "REPLACE", "RENAME", "GRANT", "REVOKE", "EXEC", "EXECUTE",
    "MERGE", "LOAD", "INTO OUTFILE", "INTO DUMPFILE", "SHUTDOWN"
);
```

用正则 `\\bkeyword\\b` 做**整词匹配**，避免误杀。例如：SQL 中有一个字段名叫 `create_time`，包含子串 `CREATE`，但如果用整词匹配 `\bCREATE\b` 就不会误拦截 `create_time`，因为 `create` 后面紧跟着 `_` 而不是词边界。

### 8.2 第二道防线：SQL 注入特征检测

```java
private static final Pattern[] INJECTION_PATTERNS = {
    Pattern.compile("'.*OR\\s+'1'\\s*=\\s*'1", ...),  // ' OR '1'='1
    Pattern.compile("UNION\\s+SELECT", ...),            // UNION SELECT
    Pattern.compile("--\\s*$", ...),                     // 行注释结尾
    Pattern.compile("/\\*.*\\*/", ...),                  // 块注释
    Pattern.compile(";\\s*(DROP|DELETE|...)", ...),      // 语句截断后跟高危操作
};
```

### 8.3 第三道防线：表白名单

```java
private static final Set<String> ALLOWED_TABLES = Set.of("order_info");
```

提取 SQL 中所有 `FROM` 和 `JOIN` 后的表名，逐一检查是否在白名单中。如果引用了 `information_schema` 或 `mysql.user` 等系统表，直接拦截。

### 8.4 为什么要用三道防线（纵深防御思想）

```
用户问题 → 大模型生成SQL → [防线1: 高危关键字] → [防线2: 注入特征] → [防线3: 表白名单] → 执行
```

单一防线可能被绕过：大模型可能被 Prompt Injection 诱导生成危险 SQL。三道不同层级的校验让攻击面降到最低。即使某一道防线有漏洞或误判，另外两道依然能兜底。

---

## 9. 工具执行器 — ToolExecutor

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolExecutor {
    private final OrderInfoMapper orderInfoMapper;

    public List<Map<String, Object>> executeQuery(String sql) {
        long start = System.currentTimeMillis();
        log.info("执行SQL查询: {}", sql);
        List<Map<String, Object>> result = orderInfoMapper.executeRawSql(sql);
        long elapsed = System.currentTimeMillis() - start;
        log.info("查询完成，返回 {} 行数据，耗时 {}ms", result.size(), elapsed);
        return result;
    }
}
```

**设计原则**：

- **安全前提**：调用方必须先在 `SqlSecurityValidator.validate()` 中校验通过，`ToolExecutor` 不再二次校验
- **单一职责**：只负责执行 SQL 和记录日志，不关心 SQL 从哪来、结果用来做什么
- **可扩展**：新增工具（如调用外部 API、生成文件）只需新增方法，不修改现有代码

---

## 10. 会话记忆管理 — SessionManager

### 10.1 数据结构

```java
@Component
public class SessionManager {
    private static final int MAX_HISTORY_TURNS = 10;

    // 核心存储：sessionId → 对话历史列表
    private final Map<String, List<Exchange>> sessions = new ConcurrentHashMap<>();

    @Data
    public static class Exchange {
        private String question;       // 用户问题
        private String answer;         // AI 回答
        private LocalDateTime timestamp;
    }
}
```

### 10.2 会话创建

```java
public String createSession() {
    String sessionId = UUID.randomUUID().toString().replace("-", "");
    sessions.put(sessionId, new ArrayList<>());
    return sessionId;
}
```

用 `UUID.randomUUID()` 生成全局唯一 ID，去掉连字符使 URL 更简洁。

### 10.3 历史记录获取（多轮对话上下文拼接）

```java
public List<Message> getHistory(String sessionId) {
    List<Exchange> history = sessions.get(sessionId);
    if (history == null || history.isEmpty()) {
        return Collections.emptyList();
    }

    List<Message> messages = new ArrayList<>();
    for (Exchange ex : history) {
        messages.add(Message.user(ex.getQuestion()));       // 用户问了什么
        messages.add(Message.assistant(ex.getAnswer()));    // AI 答了什么
    }
    return messages;
}
```

这段代码把多轮对话转换为 OpenAI 格式的 user/assistant 交替消息，拼接到新请求的 messages 数组中，实现**上下文关联提问**：

```
第1轮：用户"本月订单总数？" → AI"共 38 笔"
第2轮：用户"其中异常的有多少？" → AI 能从上下文知道"其中"指的是"本月订单"中的
```

### 10.4 上下文窗口管理

```java
public void addExchange(String sessionId, String question, String answer) {
    List<Exchange> history = sessions.computeIfAbsent(sessionId, k -> new ArrayList<>());
    history.add(new Exchange(question, answer));
    while (history.size() > MAX_HISTORY_TURNS) {
        history.remove(0);  // 删除最早的记录，防止 token 溢出
    }
}
```

**为什么要限制历史轮次？**
大模型 API 按 token 计费，且上下文窗口有上限。如果不限制，长对话会导致请求越来越慢、越来越贵，甚至超出模型的最大上下文长度导致报错。

### 10.5 服务无状态 → 有状态的升级路径

当前使用 `ConcurrentHashMap` 内存存储，服务重启后所有会话丢失。升级路径：

```
ConcurrentHashMap → Redis（持久化 + 分布式共享 + TTL 自动过期）
```

接口签名不变（`createSession()`, `getHistory()`, `addExchange()`），只需替换实现类即可。

---

## 11. Agent 调度核心 — AgentServiceImpl

这是**整个项目最重要的类**，约 260 行代码实现了完整的 Agent 闭环。

### 11.1 完整调度流程

```java
@Override
public AgentResponse chat(String sessionId, String question) {
    // Step 0: 会话初始化
    if (sessionId == null || sessionId.isBlank() || !sessionManager.sessionExists(sessionId)) {
        sessionId = sessionManager.createSession();
    }

    try {
        // Step 1: 拼接消息列表（System Prompt + 历史 + 当前问题）
        List<Message> messages = buildInitialMessages(sessionId, question);

        // Step 2: 第一次大模型调用 → 决策 + SQL 生成
        String llmRawResponse = llmClient.chat(messages);

        // Step 3: 解析大模型的结构化 JSON 输出
        LlmResponse llmResponse = parseLlmResponse(llmRawResponse);

        // Step 4: 如果不需要 SQL（闲聊/咨询类），直接返回
        if (!llmResponse.isNeedSql()) {
            sessionManager.addExchange(sessionId, question, llmResponse.getAnswer());
            return AgentResponse.success(sessionId, llmResponse.getAnswer(),
                    null, llmResponse.getReasoning(), null);
        }

        // Step 5: SQL 安全校验
        sqlValidator.validate(llmResponse.getSql());

        // Step 6: 执行数据库查询
        List<Map<String, Object>> queryResult = toolExecutor.executeQuery(sql);

        // Step 7: 第二次大模型调用 → 数据分析 + 结论生成
        String analysisConclusion = generateAnalysis(question, queryResult);

        // Step 8: 保存会话记录
        sessionManager.addExchange(sessionId, question, analysisConclusion);

        return AgentResponse.success(sessionId, analysisConclusion,
                queryResult, llmResponse.getReasoning(), sql);

    } catch (IllegalArgumentException e) {
        // SQL 安全校验失败（专用错误码 4001）
        return AgentResponse.fail(4001, "查询请求未通过安全检查：" + e.getMessage());
    } catch (Exception e) {
        // 通用兜底异常（错误码 5000）
        return AgentResponse.fail(5000, "系统处理请求时遇到问题：" + e.getMessage());
    }
}
```

### 11.2 系统提示词设计（Prompt Engineering）

```java
private static final String SYSTEM_PROMPT = """
        你是一个专业的业务数据查询分析助手...

        ## 可访问的数据库表
        表名：order_info（订单信息表）
        字段说明：
        - id: bigint, 主键ID
        - order_no: varchar(32), 订单编号
        - order_amount: decimal(12,2), 订单金额（元）
        - order_status: tinyint, 订单状态（0=异常, 1=正常）
        - create_time: datetime, 创建时间

        ## 工作规则
        1. 首先判断用户问题是否需要查询数据库
        2. 生成SQL时，只生成SELECT语句
        3. 涉及时间的查询，使用CURDATE()、DATE_SUB()等MySQL函数
        4. 查询结果限制最多返回500行（LIMIT 500）

        ## 输出格式
        你必须严格按照以下JSON格式输出，不要包含其他任何文字：
        {"needSql": true, "reasoning": "...", "sql": "SELECT ...", "answer": ""}
        {"needSql": false, "reasoning": "...", "sql": "", "answer": "直接回答"}
        """;
```

**Prompt 设计的核心技巧**：

1. **角色设定**（"你是一个专业的业务数据查询分析助手"）：激活模型的特定能力
2. **数据字典**（表结构 + 字段说明）：让模型知道能查询什么数据、字段的含义
3. **约束条件**（只生成 SELECT、使用 MySQL 函数、LIMIT 500）：缩小模型的"发挥空间"，减少幻觉
4. **强制 JSON 输出**（"不要包含其他任何文字"）：确保程序能稳定解析。配合 `temperature: 0.1` 使用
5. **状态码含义**（"order_status: 0=异常, 1=正常"）：这些业务含义模型不可能知道，必须在 Prompt 中明确告知

### 11.3 JSON 提取容错

```java
private LlmResponse parseLlmResponse(String rawResponse) {
    try {
        // 容错：大模型可能在 JSON 外加说明文字
        String jsonStr = JsonUtil.extractJson(rawResponse);
        // 示例输入："好的，以下是查询结果：\n{"needSql": true, ...}"
        // 提取后：  "{"needSql": true, ...}"
        LlmResponse resp = JSON.parseObject(jsonStr, LlmResponse.class);
        if (!resp.isValid()) {
            throw new RuntimeException("大模型返回的JSON结构不完整");
        }
        return resp;
    } catch (Exception e) {
        throw new RuntimeException("大模型返回格式异常，无法解析为结构化指令。请重试。", e);
    }
}
```

**为什么需要这个容错机制？**

即使 Prompt 里写了"不要包含其他任何文字"，大模型有时仍然会在 JSON 前后添加文字（尤其是长对话场景中模型"忘记"了约束）。`extractJson()` 通过找第一个 `{` 和最后一个 `}` 来提取 JSON 子串，是一种**实用的兜底策略**。

### 11.4 第二次推理：数据分析

```java
private String generateAnalysis(String originalQuestion,
                                 List<Map<String, Object>> queryResult) {
    String dataJson = JSON.toJSONString(queryResult);

    // 数据截断保护：避免超出 Token 限制
    if (dataJson.length() > 8000) {
        dataJson = dataJson.substring(0, 8000) + "...(数据已截断，共"
                + queryResult.size() + "行)";
    }

    String prompt = String.format("""
            查询结果数据如下（JSON数组格式）：
            %s

            请基于以上真实数据，对用户的原始问题进行专业分析：

            **用户问题**：%s

            **分析要求**：
            1. 金额数据保留2位小数，添加千分位格式
            2. 如有异常数据，特别指出并分析
            3. 如果查询结果为空，告知用户并给出建议

            请直接输出分析结论，不要包含JSON格式。
            """, dataJson, originalQuestion);

    List<Message> messages = List.of(
            Message.system("你是一个专业的数据分析师..."),
            Message.user(prompt)
    );

    return llmClient.chat(messages);
}
```

**数据截断保护**：大模型 API 有 max_tokens 限制，如果查询结果数据量太大（比如返回了 1000 行数据），直接全部传给模型会超出限制。8000 字符是一个经验值，确保在大多数模型的上下文窗口内。

---

## 12. 数据层 — Entity & Mapper

### 12.1 实体类 `OrderInfo.java`

```java
@Data
@TableName("order_info")          // 映射到 order_info 表
public class OrderInfo {
    @TableId(type = IdType.AUTO)  // 主键自增
    private Long id;
    private String orderNo;        // 下划线自动映射为驼峰：order_no → orderNo
    private BigDecimal orderAmount;
    private Integer orderStatus;
    private LocalDateTime createTime;

    public String getStatusDesc() {
        return orderStatus != null && orderStatus == 1 ? "正常" : "异常";
    }
}
```

**关键点**：金额字段用 `BigDecimal` 而不是 `double`/`float`。二进制浮点数存在精度问题（`0.1 + 0.2 ≠ 0.3`），金融场景必须用 `BigDecimal`。

### 12.2 Mapper `OrderInfoMapper.java`

```java
@Mapper
public interface OrderInfoMapper extends BaseMapper<OrderInfo> {

    // ★ 核心方法：执行动态 SQL
    @Select("${sql}")
    List<Map<String, Object>> executeRawSql(@Param("sql") String sql);

    // 按日期范围查询
    @Select("SELECT * FROM order_info WHERE create_time >= #{startTime} AND create_time < #{endTime}")
    List<OrderInfo> findByDateRange(@Param("startTime") String startTime,
                                    @Param("endTime") String endTime);
}
```

**`${sql}` vs `#{sql}` 的区别**：

| 写法 | 效果 | 安全性 |
|---|---|---|
| `#{sql}` | 将 sql 作为**参数占位符**替换（带引号），如 `SELECT * FROM ?` | 安全，但这里不能用，因为整个 SQL 是一个参数 |
| `${sql}` | 将 sql **直接拼接**到 SQL 字符串中 | **有注入风险**，所以在调用前必须经过 `SqlSecurityValidator` |

这里用 `${sql}` 是因为需要执行整个动态 SQL 字符串，这也是为什么安全校验是**强制性的**。

---

## 13. 完整请求链路时序图

```
用户               Controller        AgentServiceImpl    LlmClient      SqlSecurity    ToolExecutor    SessionManager
 │                     │                    │                │               │              │               │
 │ POST /agent/chat    │                    │                │               │              │               │
 ├────────────────────►│                    │                │               │              │               │
 │                     │ chat(sid, q)       │                │               │              │               │
 │                     ├───────────────────►│                │               │              │               │
 │                     │                    │ createSession()│               │              │               │
 │                     │                    ├───────────────────────────────────────────────────────────────►│
 │                     │                    │ ◄─── sessionId │               │              │               │
 │                     │                    │                │               │              │               │
 │                     │                    │ getHistory()   │               │              │               │
 │                     │                    ├───────────────────────────────────────────────────────────────►│
 │                     │                    │ ◄── messages   │               │              │               │
 │                     │                    │                │               │              │               │
 │                     │                    │ chat(messages) │               │              │               │
 │                     │                    ├───────────────►│               │              │               │
 │                     │                    │                │  HTTP POST    │              │               │
 │                     │                    │                │  大模型API     │              │               │
 │                     │                    │ ◄── rawJson    │               │              │               │
 │                     │                    │                │               │              │               │
 │                     │                    │ parseLlmResponse()             │              │               │
 │                     │                    │ extractJson + 反序列化          │              │               │
 │                     │                    │                │               │              │               │
 │                     │                    │ validate(sql)  │               │              │               │
 │                     │                    ├───────────────────────────────►│              │               │
 │                     │                    │ ◄── 通过/抛异常                  │              │               │
 │                     │                    │                │               │              │               │
 │                     │                    │ executeQuery(sql)              │              │               │
 │                     │                    ├──────────────────────────────────────────────►│               │
 │                     │                    │ ◄── List<Map>   │               │              │               │
 │                     │                    │                │               │              │               │
 │                     │                    │ generateAnalysis(question, data)              │               │
 │                     │                    ├───────────────►│               │              │               │
 │                     │                    │                │  HTTP POST    │              │               │
 │                     │                    │                │  大模型API     │              │               │
 │                     │                    │ ◄── conclusion │               │              │               │
 │                     │                    │                │               │              │               │
 │                     │                    │ addExchange()  │               │              │               │
 │                     │                    ├───────────────────────────────────────────────────────────────►│
 │                     │                    │                │               │              │               │
 │                     │ ◄── AgentResponse  │                │               │              │               │
 │ ◄── JSON response   │                    │                │               │              │               │
```

---

## 14. 如何扩展项目

### 14.1 新增业务表

1. 在 MySQL 中创建新表
2. 创建对应的 Entity 类和 Mapper 接口
3. 在 `SqlSecurityValidator.ALLOWED_TABLES` 中加入新表名
4. 在 `SYSTEM_PROMPT` 的表结构描述中加入新表的字段说明

### 14.2 新增工具能力（如调用外部 API）

1. 在 `ToolExecutor` 中新增方法（如 `callExternalApi()`）
2. 在 `AgentServiceImpl.SYSTEM_PROMPT` 中加入新工具的描述，让大模型知道有新的工具可用
3. 在 `LlmResponse` 中加入新工具相关的字段（或改用更通用的工具调用格式）
4. 在 `AgentServiceImpl.chat()` 中加入新工具的路由逻辑

### 14.3 升级为 Redis 会话持久化

```java
// 只需新建一个实现类，替换 SessionManager
@Component
public class RedisSessionManager extends SessionManager {
    private final RedisTemplate<String, List<Exchange>> redisTemplate;

    // 所有方法改用 Redis 操作
}
```

### 14.4 扩展方向建议

- **RAG 知识库**：将业务文档向量化，大模型生成 SQL 前先检索相关文档
- **定时报表**：配合 XXL-Job / Quartz 定时执行固定的数据查询，自动生成日报
- **多 Agent 协作**：数据查询 Agent + 文案撰写 Agent + 图表生成 Agent 分工
- **前端 Dashboard**：React / Vue 搭建可视化查询界面，展示表格和图表
- **接入 Spring AI**：用框架重构 LmmClient 部分，简化模型切换逻辑

---

## 15. 教学重点 FAQ

### Q1: 为什么不直接用 LangChain4j，要手写 Agent 调度？

**答**：框架是黑盒,"开箱即用"意味着你不知道里面发生了什么。本项目所有核心逻辑只有 ~260 行的 `AgentServiceImpl`，读完就能理解 Agent 是怎么运作的。先懂原理，再用框架可以让你成为更好的框架使用者——出问题时你能定位到根本原因。

### Q2: 大模型生成的 SQL 不对怎么办？

**答**：有几层保障：
1. System Prompt 中提供了完整的表结构和字段说明，`temperature: 0.1` 保证了输出的确定性
2. SQL 安全校验会在执行前拦截非法 SQL
3. 如果查询结果为空或异常，第二次大模型推理会告知用户，不会返回错误数据
4. 响应中的 `executedSql` 字段让开发者/用户可以检查 SQL 是否正确

### Q3: 并发场景下 SessionManager 安全吗？

**答**：安全。`ConcurrentHashMap` 保证了多线程下的 put/get/remove 操作的原子性。`computeIfAbsent` 是原子操作，不会出现两个线程同时创建同一 session 的问题。`addExchange` 中的 `while` 循环删除最早记录是安全的，因为 ConcurrentHashMap 的迭代器是弱一致的。

### Q4: 如果大模型不返回 JSON 怎么办？

**答**：`JsonUtil.extractJson()` 会通过查找第一个 `{` 和最后一个 `}` 来提取 JSON 子串。如果仍然失败（比如大模型完全没有输出 JSON），`parseLlmResponse()` 会抛出 RuntimeException，被外层 catch 捕获后返回错误码 5000 给用户。这是一个**优雅降级**的设计。

### Q5: 为什么用 OkHttp 而不是 Spring 自带的 RestClient？

**答**：
- OkHttp 是当前 Java 生态中最流行的 HTTP 客户端之一
- Spring 6.1 之前的 `RestTemplate` 已进入维护模式
- `WebClient` 需要引入 Spring WebFlux（响应式编程），对新手不友好
- OkHttp 的 API 直观、学习成本低、和本项目轻量化的理念一致

### Q6: SQL 安全校验的三道防线是怎么想到的？

**答**：这是**纵深防御**思想在 SQL 校验中的应用，参考了网络安全中的多层防火墙设计。AI 生成的 SQL 是不可信任的外部输入，用单一防线（如只检查 DELETE 关键字）很容易被绕过（如用 `/**/` 注释拆开关键字）。三层不同维度的校验（关键字、注入特征、表范围）让攻击面大幅缩小。

### Q7: 这项目的代码量有多少？适合什么水平的开发者？

**答**：
- Java 源码：约 1200 行（含空行和注释）
- 配置文件：约 50 行
- SQL 脚本：约 100 行

适合 **掌握了 SpringBoot 基础、写过 CRUD 项目，想进一步学习 AI Agent 开发** 的 Java 后端开发者。如果你刚学完 SpringBoot 的 Controller → Service → Mapper 分层，这个项目是你的**完美进阶练习**。

---

## 附录：快速运行指南

### 环境要求

- JDK 17+
- MySQL 8.0+
- Maven 3.8+

### 运行步骤

```bash
# 1. 创建数据库并导入测试数据
mysql -u root -p < sql/init.sql

# 2. 修改 application.yml 中的数据库密码和 LLM API Key

# 3. 启动项目
mvn clean compile spring-boot:run

# 4. 测试
curl -X POST http://localhost:8088/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"question": "本月订单总数和总金额是多少？"}'

# 5. 多轮对话测试（传入返回的 sessionId）
curl -X POST http://localhost:8088/agent/chat \
  -H "Content-Type: application/json" \
  -d '{"sessionId": "上一步返回的sessionId", "question": "其中异常订单有几笔？"}'
```

---

> **学习建议**：建议按以下顺序阅读源码
> 1. `application.yml`（了解配置）
> 2. `AgentRequest.java` / `AgentResponse.java` / `LlmResponse.java`（理解数据模型）
> 3. `AgentController.java`（看入口）
> 4. `AgentServiceImpl.java`（核心调度，重点精读）
> 5. `LlmClient.java`（理解大模型交互）
> 6. `SqlSecurityValidator.java`（理解安全设计）
> 7. `SessionManager.java`（理解会话管理）
> 8. 其余文件（Entity、Mapper、ToolExecutor）

遇到任何问题，欢迎提 Issue 或 PR。
