# AI 模块实施文档

> 本文档整理自 `scheduled-task` 项目的 AI 模块，用于在其他项目中复现相同能力。
> 当前模块覆盖：多厂商 AI 配置管理、AI 对话（自然语言 → SQL → 图表）、数据字典同步、自然语言配置生成、Markdown 渲染。

---

## 一、模块能力清单

| 能力 | 说明 |
|------|------|
| AI 配置管理 | 支持 OpenAI / Anthropic / Azure OpenAI / Ollama / 自定义兼容 OpenAI 的 API |
| AI 对话 | 支持连续上下文；关联数据源后可基于表结构生成 SQL、执行并返回 Markdown 表格 |
| 图表生成 | 根据 SQL 结果自动生成 bar/line/pie/area/scatter/stacked_bar/doughnut 图片 |
| 数据字典同步 | 从数据源抽取表结构，通过 AI 整理为 Markdown 数据字典文档并持久化 |
| 自然语言配置生成 | 一句话生成任务、网页爬取、通知规则配置 |
| 通知优化 | 使用 AI 优化邮件 / 企业微信标题和正文 |
| 意图解析 | 将自然语言解析为 VIEW_TASKS / TRIGGER_TASK / VIEW_LOGS / CREATE_TASK / QUERY_DATA 等动作 |

---

## 二、技术栈与依赖

### 2.1 后端

- Java 17
- Spring Boot 3.2.5
- MyBatis-Plus 3.5.7（实体 / Mapper / Service 基类）
- Flyway 10.x（数据库迁移）
- Fastjson2 2.0.46
- XChart 3.8.8（图表生成）
- Lombok

### 2.2 Maven 依赖

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
    <version>3.5.7</version>
</dependency>
<dependency>
    <groupId>com.alibaba.fastjson2</groupId>
    <artifactId>fastjson2</artifactId>
    <version>2.0.46</version>
</dependency>
<dependency>
    <groupId>org.knowm.xchart</groupId>
    <artifactId>xchart</artifactId>
    <version>3.8.8</version>
</dependency>
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>
</dependency>
```

### 2.3 前端

- Vue 3 + TypeScript + Vite
- Element Plus
- `marked` 18.x（Markdown 解析）
- `dompurify` 3.x（HTML 消毒）

```bash
npm install marked dompurify
npm install -D @types/marked @types/dompurify
```

---

## 三、数据库设计

### 3.1 AI 配置表

```sql
CREATE TABLE IF NOT EXISTS ai_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_name VARCHAR(255) NOT NULL COMMENT '配置名称',
    provider VARCHAR(32) NOT NULL COMMENT '厂商：OPENAI/ANTHROPIC/AZURE_OPENAI/OLLAMA/CUSTOM',
    api_key VARCHAR(512) COMMENT 'API Key',
    base_url VARCHAR(255) COMMENT 'Base URL，空则使用默认',
    model VARCHAR(128) COMMENT '模型名',
    temperature DOUBLE DEFAULT 0.7,
    max_tokens INT DEFAULT 2048,
    timeout_seconds INT DEFAULT 60,
    is_default TINYINT DEFAULT 0 COMMENT '是否默认',
    status TINYINT DEFAULT 1 COMMENT '状态：1启用 0禁用',
    system_prompt TEXT COMMENT '系统提示词',
    remark VARCHAR(500),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_provider (provider)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 配置';
```

### 3.2 AI 知识文档表

```sql
CREATE TABLE IF NOT EXISTS ai_knowledge_doc (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    datasource_id BIGINT COMMENT '关联数据源 ID',
    doc_type VARCHAR(32) NOT NULL COMMENT '文档类型：SCHEMA 等',
    title VARCHAR(255) NOT NULL,
    file_path VARCHAR(512) COMMENT '文档文件本地路径',
    status TINYINT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_datasource_id (datasource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 知识文档';
```

### 3.3 AI 会话表

```sql
CREATE TABLE IF NOT EXISTS ai_conversation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL UNIQUE,
    user_id BIGINT,
    title VARCHAR(255),
    datasource_id BIGINT,
    doc_id BIGINT COMMENT '当前使用的知识文档 ID',
    messages JSON COMMENT '会话消息列表 JSON',
    status TINYINT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_session_id (session_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 会话';
```

### 3.4 数据源表（节选 AI 相关字段）

```sql
CREATE TABLE IF NOT EXISTS datasource_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    db_type VARCHAR(32) NOT NULL COMMENT 'mysql/postgresql/oracle/sqlserver',
    host VARCHAR(128) NOT NULL,
    port INT NOT NULL,
    database_name VARCHAR(128) NOT NULL,
    username VARCHAR(128) NOT NULL,
    password VARCHAR(512),
    driver_class VARCHAR(256),
    status TINYINT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

## 四、后端实现

### 4.1 目录结构建议

```
com.example.project
├── ai
│   ├── AiClient.java
│   ├── AiClientFactory.java
│   ├── AiChatRequest.java
│   ├── AiChatResponse.java
│   ├── AiMessage.java
│   ├── OpenAiCompatibleClient.java
│   └── AnthropicClient.java
├── config
│   └── WebMvcConfig.java
├── controller
│   ├── AiConfigController.java
│   ├── AssistantController.java
│   └── DatasourceConfigController.java
├── dto
│   ├── IntentResult.java
│   └── SqlGenerateResult.java
├── entity
│   ├── AiConfig.java
│   ├── AiConversation.java
│   ├── AiKnowledgeDoc.java
│   └── BaseEntity.java
├── mapper
│   ├── AiConfigMapper.java
│   ├── AiConversationMapper.java
│   └── AiKnowledgeDocMapper.java
├── service
│   ├── AiConfigService.java
│   ├── AiAssistantService.java
│   ├── AiConversationService.java
│   ├── AiKnowledgeDocService.java
│   ├── AiKnowledgeDocStorageService.java
│   ├── AiAutoConfigService.java
│   ├── ChartGenerationService.java
│   ├── DatasourceConfigService.java
│   └── DatasourceSchemaService.java
├── storage
│   └── controller
│       └── StorageFileController.java
└── task
    └── SqlExecutor.java
```

### 4.2 AI 抽象层

#### 4.2.1 AiMessage

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiMessage {
    public static final String ROLE_SYSTEM = "system";
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";

    private String role;
    private String content;

    public static AiMessage system(String content) { return new AiMessage(ROLE_SYSTEM, content); }
    public static AiMessage user(String content) { return new AiMessage(ROLE_USER, content); }
    public static AiMessage assistant(String content) { return new AiMessage(ROLE_ASSISTANT, content); }
}
```

#### 4.2.2 AiChatRequest / AiChatResponse

```java
@Data
public class AiChatRequest {
    private String model;
    private List<AiMessage> messages = new ArrayList<>();
    private Double temperature = 0.7;
    private Integer maxTokens = 2048;

    public static AiChatRequest of(String model, List<AiMessage> messages) {
        AiChatRequest request = new AiChatRequest();
        request.setModel(model);
        request.setMessages(messages);
        return request;
    }
}

@Data
public class AiChatResponse {
    private String content;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private String errorMessage;

    public static AiChatResponse error(String message) {
        AiChatResponse response = new AiChatResponse();
        response.setErrorMessage(message);
        return response;
    }

    public boolean isSuccess() { return errorMessage == null; }
}
```

#### 4.2.3 AiClient 接口

```java
public interface AiClient {
    AiChatResponse chat(AiChatRequest request);

    default AiChatResponse chat(String userMessage) {
        return chat(AiChatRequest.of(null, List.of(AiMessage.user(userMessage))));
    }

    default AiChatResponse chat(List<AiMessage> messages) {
        return chat(AiChatRequest.of(null, messages));
    }
}
```

#### 4.2.4 AiClientFactory

```java
@Component
public class AiClientFactory {
    public static final String PROVIDER_OPENAI = "OPENAI";
    public static final String PROVIDER_ANTHROPIC = "ANTHROPIC";
    public static final String PROVIDER_AZURE_OPENAI = "AZURE_OPENAI";
    public static final String PROVIDER_OLLAMA = "OLLAMA";
    public static final String PROVIDER_CUSTOM = "CUSTOM";

    public AiClient createClient(AiConfig config) {
        if (config == null) throw new IllegalArgumentException("AI 配置不能为空");
        String provider = config.getProvider() != null ? config.getProvider().toUpperCase() : "";
        String baseUrl = resolveBaseUrl(config);
        String apiKey = config.getApiKey();
        String model = config.getModel();
        Integer timeout = config.getTimeoutSeconds();

        return switch (provider) {
            case PROVIDER_OPENAI, PROVIDER_AZURE_OPENAI, PROVIDER_CUSTOM ->
                new OpenAiCompatibleClient(baseUrl, apiKey, model, timeout);
            case PROVIDER_OLLAMA ->
                new OpenAiCompatibleClient(baseUrl, null, model, timeout);
            case PROVIDER_ANTHROPIC ->
                new AnthropicClient(baseUrl, apiKey, model, timeout);
            default -> throw new IllegalArgumentException("不支持的 AI 厂商: " + config.getProvider());
        };
    }

    private String resolveBaseUrl(AiConfig config) {
        if (StringUtils.hasText(config.getBaseUrl())) return config.getBaseUrl();
        return switch (config.getProvider().toUpperCase()) {
            case PROVIDER_OPENAI -> "https://api.openai.com/v1";
            case PROVIDER_ANTHROPIC -> "https://api.anthropic.com/v1";
            case PROVIDER_AZURE_OPENAI -> "";
            case PROVIDER_OLLAMA -> "http://localhost:11434/v1";
            default -> "";
        };
    }
}
```

#### 4.2.5 OpenAI 兼容客户端

```java
@Slf4j
public class OpenAiCompatibleClient implements AiClient {
    private final String baseUrl;
    private final String apiKey;
    private final String defaultModel;
    private final Integer timeoutSeconds;
    private final RestTemplate restTemplate;

    public OpenAiCompatibleClient(String baseUrl, String apiKey, String defaultModel, Integer timeoutSeconds) {
        this.baseUrl = baseUrl != null ? baseUrl.replaceAll("/+$", "") : "https://api.openai.com/v1";
        this.apiKey = apiKey;
        this.defaultModel = defaultModel;
        this.timeoutSeconds = timeoutSeconds != null ? timeoutSeconds : 60;
        this.restTemplate = new RestTemplate();
        this.restTemplate.getMessageConverters().add(new StringHttpMessageConverter());
    }

    @Override
    public AiChatResponse chat(AiChatRequest request) {
        String url = baseUrl + "/chat/completions";
        Map<String, Object> body = new HashMap<>();
        body.put("model", request.getModel() != null ? request.getModel() : defaultModel);
        body.put("messages", request.getMessages().stream()
                .map(m -> Map.of("role", m.getRole(), "content", m.getContent()))
                .collect(Collectors.toList()));
        if (request.getTemperature() != null) body.put("temperature", request.getTemperature());
        if (request.getMaxTokens() != null) body.put("max_tokens", request.getMaxTokens());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.isEmpty()) headers.setBearerAuth(apiKey);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
            return parseResponse(response.getBody());
        } catch (Exception e) {
            log.error("OpenAI compatible API call failed: {}", url, e);
            return AiChatResponse.error("AI 调用失败: " + e.getMessage());
        }
    }

    private AiChatResponse parseResponse(String json) {
        try {
            JSONObject root = JSON.parseObject(json);
            if (root.containsKey("error")) {
                return AiChatResponse.error(root.getJSONObject("error").getString("message"));
            }
            JSONObject message = root.getJSONArray("choices").getJSONObject(0).getJSONObject("message");
            AiChatResponse response = new AiChatResponse();
            response.setContent(message.getString("content"));
            JSONObject usage = root.getJSONObject("usage");
            if (usage != null) {
                response.setPromptTokens(usage.getInteger("prompt_tokens"));
                response.setCompletionTokens(usage.getInteger("completion_tokens"));
                response.setTotalTokens(usage.getInteger("total_tokens"));
            }
            return response;
        } catch (Exception e) {
            log.error("Parse AI response failed: {}", json, e);
            return AiChatResponse.error("解析 AI 响应失败");
        }
    }
}
```

#### 4.2.6 Anthropic 客户端

核心差异：
- endpoint 为 `/messages`
- system prompt 需单独放在 `system` 字段
- messages 中不能包含 `role=system`
- 认证头为 `x-api-key` 与 `anthropic-version: 2023-06-01`

```java
@Slf4j
public class AnthropicClient implements AiClient {
    // ... 构造、chat、parseResponse ...
    // 详见源码：src/main/java/com/mattoid/scheduled/ai/AnthropicClient.java
}
```

### 4.3 实体 / Mapper / Service 基类

使用 MyBatis-Plus：`BaseMapper<T>` + `ServiceImpl<M, T>`。

```java
@Data
public class BaseEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_config")
public class AiConfig extends BaseEntity {
    private String configName;
    private String provider;
    private String apiKey;
    private String baseUrl;
    private String model;
    private Double temperature;
    private Integer maxTokens;
    private Integer timeoutSeconds;
    private Integer isDefault;
    private Integer status;
    private String systemPrompt;
    private String remark;
}
```

### 4.4 AI 配置服务

```java
@Service
public class AiConfigService extends ServiceImpl<AiConfigMapper, AiConfig> {
    private final AiClientFactory aiClientFactory;

    public AiConfig getDefaultConfig() {
        return lambdaQuery()
                .eq(AiConfig::getStatus, 1)
                .eq(AiConfig::getIsDefault, 1)
                .one();
    }

    public AiConfig getEffectiveConfig(Long aiConfigId) {
        if (aiConfigId != null) {
            AiConfig config = getById(aiConfigId);
            if (config != null && config.getStatus() != null && config.getStatus() == 1) return config;
        }
        return getDefaultConfig();
    }

    public AiChatResponse testConfig(AiConfig config) {
        AiClient client = aiClientFactory.createClient(config);
        return client.chat("你好，请简要回复 hello");
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean saveOrUpdateConfig(AiConfig config) {
        if (config.getIsDefault() != null && config.getIsDefault() == 1) {
            lambdaUpdate().set(AiConfig::getIsDefault, 0)
                    .ne(config.getId() != null, AiConfig::getId, config.getId())
                    .update();
        }
        if (!StringUtils.hasText(config.getApiKey()) && config.getId() != null) {
            AiConfig old = getById(config.getId());
            if (old != null) config.setApiKey(old.getApiKey());
        }
        return saveOrUpdate(config);
    }
}
```

### 4.5 AI 助手服务

`AiAssistantService` 提供以下核心方法：

| 方法 | 用途 |
|------|------|
| `parseIntent(String)` | 解析用户意图为 JSON：action / params / summary |
| `extractQueryParams(String)` | 提取业务过滤参数（排除时间范围） |
| `optimizeNotification(subject, body, context, aiConfigId)` | 优化通知内容 |
| `chatReply(String)` | 通用闲聊回复 |
| `generateSchemaDoc(String)` | 将原始表结构整理为 Markdown 数据字典 |
| `generateSql(schemaDoc, question, history)` | 生成 SELECT SQL、参数、图表类型 |
| `generateConfig(String)` | 一句话生成系统配置 JSON |

关键提示词设计：

- **SQL 生成**：要求只返回 JSON，SQL 中参数使用 `${name}` 占位，`chartType` 支持 bar/line/pie/area/scatter/stacked_bar/doughnut。
- **SQL 结果总结（WEB）**：要求使用 Markdown 表格展示数据。
- **SQL 结果总结（企业微信）**：要求使用纯文本，禁用 Markdown、表格、图片。
- **数据字典**：基于 `DatabaseMetaData` 抽取的原始结构生成结构化 Markdown。

### 4.6 AI 对话服务

核心流程：

```
用户发送消息
  → getOrCreate 会话
  → loadMessages 加载历史
  → 若已关联数据源且存在 SCHEMA 文档
       → aiAssistantService.generateSql 生成 SQL
       → SqlExecutor.validateReadOnlySql + executeQuery 执行
       → 若指定 chartType 且结果非空 → ChartGenerationService 生成 PNG
       → 保存图表到本地并返回 /storage/ai-charts/... URL
       → AI 总结结果并返回 Markdown
  → 否则走通用对话
  → 保存用户消息 + AI 回复到 messages JSON
```

```java
public enum ReplyChannel { WEB, WECOM }

public record ChatReplyResult(String text, File imageFile) {
    public ChatReplyResult(String text) { this(text, null); }
}
```

### 4.7 SQL 执行器

```java
@Component
public class SqlExecutor {
    private static final Pattern SQL_NON_READ_ONLY_PATTERN = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|ALTER|CREATE|TRUNCATE|MERGE|REPLACE|GRANT|REVOKE|EXEC|EXECUTE|CALL|LOAD)\\b",
            Pattern.CASE_INSENSITIVE);

    public static void validateReadOnlySql(String sql) {
        if (!StringUtils.hasText(sql)) throw new IllegalArgumentException("SQL 内容为空");
        String stripped = sql.replaceAll("(?s)/\\*.*?\\*/|--[^\\r\\n]*", " ").trim();
        if (!stripped.regionMatches(true, 0, "SELECT", 0, 6)) {
            throw new IllegalArgumentException("AI 对话仅允许执行 SELECT 查询语句");
        }
        if (SQL_NON_READ_ONLY_PATTERN.matcher(stripped).find()) {
            throw new IllegalArgumentException("AI 对话禁止执行包含 INSERT/UPDATE/DELETE/DROP/ALTER 等变更关键字的 SQL");
        }
    }

    public List<Map<String, Object>> executeQuery(Long datasourceId, String sql, Map<String, Object> params) throws Exception {
        String processedSql = processSqlVariables(sql, params);
        DataSource dataSource = datasourceConfigService.getDataSource(datasourceId);
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(processedSql)) {
            // ... 使用 LinkedHashMap 保持列顺序
        }
    }
}
```

### 4.8 图表生成服务

基于 XChart，根据查询结果自动识别分类列（第一个非数值列）和数值列。支持自动合并分类、X 轴标签旋转、透明背景。

```java
@Service
public class ChartGenerationService {
    public File generateChart(List<Map<String, Object>> data, String chartType, String title) {
        return generateChart(data, chartType, title, true, "AUTO", null);
    }
}
```

### 4.9 数据源同步数据字典

```java
@Service
public class DatasourceConfigService extends ServiceImpl<DatasourceConfigMapper, DatasourceConfig> {
    @Transactional(rollbackFor = Exception.class)
    public AiKnowledgeDoc syncSchema(Long datasourceId) throws Exception {
        DatasourceConfig config = getById(datasourceId);
        String rawSchema = datasourceSchemaService.extractSchema(config);
        String docContent = aiAssistantService.generateSchemaDoc(rawSchema);
        String filePath = aiKnowledgeDocStorageService.save(datasourceId, "SCHEMA", docContent);

        AiKnowledgeDoc doc = new AiKnowledgeDoc();
        doc.setDatasourceId(datasourceId);
        doc.setDocType("SCHEMA");
        doc.setTitle(config.getName() + " 数据字典");
        doc.setFilePath(filePath);
        doc.setStatus(1);
        aiKnowledgeDocService.save(doc);
        return doc;
    }
}
```

### 4.10 静态文件访问

图表保存路径：`${report.upload.path}/ai-charts/{yyyyMMdd}/{uuid}.png`

访问 URL：`/storage/ai-charts/{yyyyMMdd}/{uuid}.png`

```java
@Controller
@RequestMapping("/storage")
public class StorageFileController {
    @GetMapping("/**")
    public ResponseEntity<Resource> serveFile(HttpServletRequest request) {
        String path = request.getRequestURI();
        // 去掉 /storage 前缀
        Path filePath = Paths.get(uploadPath, path).normalize();
        Path basePath = Paths.get(uploadPath).normalize();
        if (!filePath.startsWith(basePath)) {
            return ResponseEntity.notFound().build(); // 防止目录穿越
        }
        // ... 返回文件
    }
}
```

### 4.11 Controller 接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/ai-config/**` | CRUD + 测试 + 默认配置 | AI 配置管理 |
| `/api/assistant/chat` | POST | AI 对话 |
| `/api/assistant/parse-intent` | POST | 意图解析 |
| `/api/assistant/optimize-notification` | POST | 优化通知 |
| `/api/assistant/generate-config` | POST | 生成配置 JSON |
| `/api/assistant/auto-configure` | POST | 生成并保存配置 |
| `/api/datasource/{id}/sync-schema` | POST | 同步数据字典 |
| `/storage/**` | GET | 访问图表/文件 |

---

## 五、前端实现

### 5.1 路由

```ts
{
  path: '/ai',
  name: 'Ai',
  redirect: '/ai/chat',
  meta: { title: 'AI 助手', icon: 'ChatDotRound', permission: null },
  children: [
    {
      path: '/ai/chat',
      name: 'AiChat',
      component: () => import('@/views/ai-chat/AiChatView.vue'),
      meta: { title: 'AI 助手', permission: 'task:view' }
    },
    {
      path: '/ai/config',
      name: 'AiConfig',
      component: () => import('@/views/ai-config/AiConfigList.vue'),
      meta: { title: 'AI 配置', permission: 'system:user' }
    }
  ]
}
```

### 5.2 AI 对话页面核心逻辑

```vue
<script setup lang="ts">
import { ref, onMounted, nextTick } from "vue";
import { marked } from "marked";
import DOMPurify from "dompurify";
import { chatWithAssistant } from "@/api/assistant";

const messages = ref<AiMessage[]>([]);
const inputMessage = ref("");
const loading = ref(false);
const selectedDatasourceId = ref<number | undefined>();
const sessionId = ref("");

const renderMarkdown = (content: string) => {
  const raw = marked.parse(content || "", { breaks: true, gfm: true }) as string;
  return DOMPurify.sanitize(raw);
};

const handleSend = async () => {
  const text = inputMessage.value.trim();
  if (!text) return;
  messages.value.push({ role: "user", content: text });
  inputMessage.value = "";
  loading.value = true;
  try {
    const res = await chatWithAssistant({
      sessionId: sessionId.value || undefined,
      datasourceId: selectedDatasourceId.value,
      message: text
    });
    sessionId.value = res.sessionId || "";
    messages.value = JSON.parse(res.messages || "[]");
  } finally {
    loading.value = false;
    scrollToBottom();
  }
};
</script>
```

### 5.3 AI 配置页面

参考 `AiConfigList.vue` + `AiConfigForm.vue`：
- 列表：分页、搜索、新增、编辑、删除、测试、默认配置开关
- 表单：配置名称、厂商、模型、API Key、Base URL、Temperature、Max Tokens、超时、系统提示词、状态、默认标记

### 5.4 API 封装

```ts
// src/api/assistant.ts
export const chatWithAssistant = (data: {
  sessionId?: string
  datasourceId?: number
  message: string
}) => request.post<AiConversation>('/assistant/chat', data)

export const autoConfigureByNaturalLanguage = (content: string) =>
  request.post<AiAutoConfigResult>('/assistant/auto-configure', { content })

// src/api/aiConfig.ts
export const pageAiConfig = (params: PageQuery) =>
  request.get<PageResult<AiConfig>>('/ai-config/page', { params })
```

---

## 六、配置项

### 6.1 application.yml

```yaml
report:
  upload:
    path: ${user.home}/scheduled-task/uploads
  ai:
    knowledge-doc:
      path: ${user.home}/scheduled-task/ai-knowledge-docs
```

### 6.2 前端环境

确保 `marked` 与 `dompurify` 已安装，并在需要渲染 Markdown 的组件中引入。

---

## 七、接入步骤

### 7.1 后端接入

1. 添加 Maven 依赖：MyBatis-Plus、Fastjson2、XChart、Lombok。
2. 创建数据库表：`ai_config`、`ai_knowledge_doc`、`ai_conversation`。
3. 复制 `ai` 包下所有类（AiClient、工厂、两种 Client、Request/Response/Message）。
4. 复制实体、Mapper、Service、Controller。
5. 配置 `report.upload.path` 与 `report.ai.knowledge-doc.path`。
6. 添加 `StorageFileController` 或等价的静态文件服务。
7. 确保数据源服务能获取 `javax.sql.DataSource` 并支持动态数据源。
8. 添加 `SqlExecutor` 并接入数据源。
9. 添加 `ChartGenerationService`。

### 7.2 前端接入

1. 安装 `marked` 与 `dompurify`。
2. 新增 AI 路由与菜单。
3. 新增 `AiChatView.vue`、`AiConfigList.vue`、`AiConfigForm.vue`。
4. 新增 API 文件 `assistant.ts`、`aiConfig.ts`。
5. 在 `types/entity.ts` 中定义 `AiConfig`、`AiConversation`、`AiKnowledgeDoc`。
6. 在 `types/assistant.ts` 中定义 `AiMessage`。

---

## 八、关键设计说明

### 8.1 多厂商兼容

- OpenAI、Azure OpenAI、Ollama、自定义均复用 `OpenAiCompatibleClient`。
- Anthropic 单独实现 `AnthropicClient`，处理 system prompt 与 messages 分离。
- 通过 `AiClientFactory.createClient(AiConfig)` 统一创建。

### 8.2 会话上下文

- 会话消息以 JSON 数组形式存储在 `ai_conversation.messages`。
- 加载时反序列化为 `List<AiMessage>`，追加新消息后序列化回写。
- SQL 生成时传入历史消息，帮助 AI 理解指代与延续查询意图。

### 8.3 安全限制

- AI 生成的 SQL 必须通过 `validateReadOnlySql`，仅允许以 `SELECT` 开头且不包含变更关键字。
- 静态文件服务必须做目录穿越校验：`filePath.startsWith(basePath)`。
- 配置表单中 API Key 编辑时留空不修改。

### 8.4 文件存储

- 数据字典文档与图表均存储在本地文件系统。
- 路径通过 `@Value` 注入，生产环境可替换为对象存储。

---

## 九、常见问题

1. **Anthropic 报错 "messages must not contain system role"**
   - 已在 `AnthropicClient.chat` 中过滤 system role 并单独传入 `system` 字段。

2. **图表不生成**
   - 检查 SQL 结果是否包含数值列；单文本结果无法生成图表。
   - 检查 `chartType` 是否在允许范围内。

3. **SQL 执行被拦截**
   - 确认 SQL 以 `SELECT` 开头，无注释中隐藏的变更关键字。

4. **图片无法显示**
   - 确认 `report.upload.path` 下存在 `/ai-charts/yyyyMMdd/*.png`。
   - 确认 `/storage/**` 接口可正常访问。

5. **切换数据源后对话仍使用旧表结构**
   - 当前设计：创建会话时绑定最新 SCHEMA 文档 `doc_id`。
   - 重新选择数据源后发送新消息会创建新会话并重新绑定。

---

## 十、源码索引

| 文件 | 路径 |
|------|------|
| AI 抽象层 | `src/main/java/com/mattoid/scheduled/ai/` |
| AI 配置 Controller | `src/main/java/com/mattoid/scheduled/controller/AiConfigController.java` |
| AI 助手 Controller | `src/main/java/com/mattoid/scheduled/controller/AssistantController.java` |
| AI 配置 Service | `src/main/java/com/mattoid/scheduled/service/AiConfigService.java` |
| AI 助手 Service | `src/main/java/com/mattoid/scheduled/service/AiAssistantService.java` |
| AI 对话 Service | `src/main/java/com/mattoid/scheduled/service/AiConversationService.java` |
| 图表生成 Service | `src/main/java/com/mattoid/scheduled/service/ChartGenerationService.java` |
| SQL 执行器 | `src/main/java/com/mattoid/scheduled/task/SqlExecutor.java` |
| 数据源同步 | `src/main/java/com/mattoid/scheduled/service/DatasourceConfigService.java` |
| 表结构抽取 | `src/main/java/com/mattoid/scheduled/service/DatasourceSchemaService.java` |
| 静态文件访问 | `src/main/java/com/mattoid/scheduled/storage/controller/StorageFileController.java` |
| 数据库迁移 | `src/main/resources/db/migration/V202607101400__add_ai_knowledge_doc_and_conversation.sql` |
| AI 对话页面 | `scheduled-task-ui/src/views/ai-chat/AiChatView.vue` |
| AI 配置列表 | `scheduled-task-ui/src/views/ai-config/AiConfigList.vue` |
| AI 配置表单 | `scheduled-task-ui/src/views/ai-config/AiConfigForm.vue` |
