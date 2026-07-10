package com.mattoid.scheduled.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.mattoid.scheduled.ai.AiChatRequest;
import com.mattoid.scheduled.ai.AiChatResponse;
import com.mattoid.scheduled.ai.AiClient;
import com.mattoid.scheduled.ai.AiClientFactory;
import com.mattoid.scheduled.ai.AiMessage;
import com.mattoid.scheduled.dto.IntentResult;
import com.mattoid.scheduled.dto.SqlGenerateResult;
import com.mattoid.scheduled.entity.AiConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mattoid.scheduled.util.TimeRangeParser;

@Slf4j
@Service
public class AiAssistantService {

    private static final String SYSTEM_PROMPT_INTENT = """
            你是一个智能报表系统助手。请理解用户输入，并只返回 JSON 格式结果，不要包含任何解释。
            可用的 action 及参数如下：
            - VIEW_TASKS: 查看任务列表。参数：keyword(可选关键词)、status(ENABLE/DISABLE)
            - TRIGGER_TASK: 手动触发任务。参数：taskId 或 taskName，可选 timeRange（如 昨天、上周、本月）
            - VIEW_LOGS: 查看执行日志。参数：taskId、status(SUCCESS/FAILED/RUNNING)、date(如 today/yesterday/2024-01-01)
            - CREATE_TASK: 创建任务。参数：taskName、triggerType(CRON/ONCE)、triggerConfig、sqlCodes(可选，逗号分隔的 SQL 编码)
            - QUERY_DATA: 查询 SQL 数据。参数：keyword(数据主题关键词，如 道达尔渠道数据)、datasource(可选，数据源名称，当用户明确指定要在某个数据源/数据库中查询时填写其名称，如 销售库、生产库)、chartType(可选：line/bar/pie)、timeRange(时间描述，如 上个月)、channel(渠道/品牌等)
            - UNKNOWN: 无法识别
            返回格式：{"action":"VIEW_TASKS","params":{"keyword":"门店"},"summary":"查看与门店相关的任务"}
            示例：用户"在销售库中查询上个月销售额前 10 的门店" -> {"action":"QUERY_DATA","params":{"datasource":"销售库","keyword":"销售额前 10 的门店","timeRange":"上个月"},"summary":"在销售库查询销售排行"}
            """;

    private static final String SYSTEM_PROMPT_EXTRACT_PARAMS = """
            请从用户查询中提取业务过滤参数。时间范围已由系统单独解析，你不需要返回 startTime 和 endTime。
            只返回一个纯 JSON 对象，不要包含任何解释。键名使用英文小驼峰，例如 channel、brand、product、region、customer。
            示例：
            用户：给我上个月的道达尔渠道数据
            输出：{"channel":"道达尔"}
            用户：查询北京地区本月销售数据
            输出：{"region":"北京"}
            如果没有任何业务参数，请返回 {}。
            """;

    private static final String SYSTEM_PROMPT_NOTIFY = """
            你是一名专业的商务沟通助手。请根据用户提供的通知标题和正文，优化表达，使其：
            1. 简洁清晰，重点突出；
            2. 语气专业、礼貌；
            3. 保留关键数据、变量占位符（如 {lastMonth}、{yyyyMMdd}、${chart:sql编码}）不变；
            4. 邮件正文支持 HTML，可适当使用段落、加粗、列表提升可读性；
            5. 只返回优化后的 JSON 结果，不要解释。
            返回格式：{"subject":"优化后的标题","body":"优化后的正文"}
            """;

    private static final String SYSTEM_PROMPT_CHAT = """
            你是企业微信智能助手，服务于定时任务报表系统。
            请根据用户消息给出简洁、友好、专业的中文回复。
            如果用户询问系统功能，可提示可用指令，例如：帮助、任务列表、运行 {任务ID}、任务日志 {任务ID}。
            """;

    private static final String SYSTEM_PROMPT_SCHEMA_DOC = """
            你是一名数据库专家。请根据下面提供的数据库表结构信息，整理成一份清晰、结构化的数据字典文档。
            文档需要包含：
            1. 数据库概述（库名、表数量）。
            2. 每张表的名称、业务用途推断、字段列表（字段名、类型、是否可空、备注）、主键信息。
            3. 表与表之间可能存在的关联关系推断。
            请使用 Markdown 格式输出，便于后续 AI 对话检索使用。

            忠实性要求（防止后续生成 SQL 时漂移）：
            - 必须逐字保留原始的表名、字段名、字段类型、是否可空、字段备注与主键信息，严禁新增、删除、改名或合并任何表/字段。
            - 业务用途、表间关联属于“推断”，请用“（推断）”明确标注，且不得用推断结果覆盖或替换真实结构；无法判断时留空，不要编造。
            - 若原始信息中已含字段备注，请原样保留，不要改写为其它含义。
            """;

    private static final String SYSTEM_PROMPT_SQL_GENERATE = """
            你是一名严谨的 SQL 专家。请严格依据提供的数据库结构文档以及用户的自然语言问题，生成一条可执行的 SQL 查询语句。
            只返回 JSON 格式结果，不要包含任何解释、不要输出 Markdown 代码块。
            返回格式：{"sql":"SELECT ...","params":{},"explanation":"简要说明","chartType":"","chartTitle":""}

            必须严格遵守的约束（防止 SQL 漂移）：
            1. 只能使用数据库结构文档中明确出现过的表名和字段名，且必须与文档中的拼写、大小写完全一致；严禁凭空捏造、推测或使用文档之外的表/字段。
            2. 字段后的“备注”仅用于理解业务含义，SQL 中必须使用真实的英文字段名，不要把中文备注当作字段名。
            3. 需要表连接时，只能基于文档中可明确判断的关系（同名外键、文档标注的主键/外键）；无法确定连接条件时宁可不连接或返回空 SQL，不要臆造关联字段。
            4. 如果用户问题所需的数据在文档中找不到对应的表或字段，必须返回 sql 为空字符串，并在 explanation 中说明“文档中缺少 XX 表/字段”，绝对不要用相近名字替代。
            5. 仅生成 SELECT 查询，禁止生成 INSERT/UPDATE/DELETE/DROP/ALTER/CREATE/TRUNCATE 等任何变更语句。
            6. SQL 中的运行时参数请使用 ${name} 占位符，并在 params 中提供默认值；表名/字段名本身不要参数化。
            7. 标识符如与数据库关键字冲突，请用反引号（MySQL）或相应引号包裹。
            8. 如果用户要求用图表展示（如柱状图、折线图、饼图等），请在 chartType 中返回图表类型：bar/line/pie/area/scatter/stacked_bar/doughnut，并在 chartTitle 中返回图表标题；否则 chartType 和 chartTitle 留空字符串。
            9. 如果无法生成，请返回 {"sql":"","params":{},"explanation":"无法生成 SQL 的原因","chartType":"","chartTitle":""}。
            """;

    private static final String SYSTEM_PROMPT_NATURAL_CONFIG = """
            你是一个定时任务报表系统配置助手。请根据用户的一句话描述，生成对应的系统配置。
            只返回 JSON 格式结果，不要包含任何解释。
            可生成的配置类型及字段如下：
            - TASK: 任务配置。字段：taskName(任务名称)、taskCode(任务编码)、description(描述)、triggerType(CRON/ONCE)、triggerConfig(cron表达式或执行时间)、taskType(SQL/CRAWL)、status(ENABLE/DISABLE)
            - NOTIFICATION_RULE: 通知规则。字段：eventType(TASK_COMPLETED/TASK_SUCCESS/TASK_FAILURE)、channel(EMAIL/WECOM_APP/WECOM_BOT/WECOM_INTELLIGENT_BOT/DINGTALK/FEISHU/SLACK/WEBHOOK)、taskCode(关联任务编码)、configCode(通知配置编码)、enabled(1/0)
            - CRAWL: 网页爬取配置。字段：name(名称)、url(目标URL)、method(GET/POST)、cronExpression(定时表达式)、status(ENABLE/DISABLE)
            返回格式：{"type":"TASK","config":{"taskName":"...",...},"summary":"配置说明"}
            如果无法识别，返回 {"type":"UNKNOWN","config":{},"summary":"无法识别配置意图"}。
            """;

    private final AiConfigService aiConfigService;
    private final AiClientFactory aiClientFactory;

    public AiAssistantService(AiConfigService aiConfigService, AiClientFactory aiClientFactory) {
        this.aiConfigService = aiConfigService;
        this.aiClientFactory = aiClientFactory;
    }

    /**
     * 解析用户自然语言意图
     */
    public IntentResult parseIntent(String userInput) {
        AiConfig config = aiConfigService.getDefaultConfig();
        if (config == null) {
            return unrecognized("未配置默认 AI");
        }

        List<AiMessage> messages = new ArrayList<>();
        messages.add(AiMessage.system(SYSTEM_PROMPT_INTENT));
        messages.add(AiMessage.user(userInput));

        AiClient client = aiClientFactory.createClient(config);
        AiChatResponse response = client.chat(AiChatRequest.of(config.getModel(), messages));
        if (!response.isSuccess()) {
            log.error("Parse intent failed: {}", response.getErrorMessage());
            return unrecognized(response.getErrorMessage());
        }

        return parseIntentJson(response.getContent());
    }

    /**
     * 从自然语言中提取查询参数，包含系统解析的时间范围以及 AI 提取的业务参数。
     */
    public Map<String, String> extractQueryParams(String userInput) {
        Map<String, String> result = new HashMap<>(TimeRangeParser.parse(userInput));

        AiConfig config = aiConfigService.getDefaultConfig();
        if (config == null) {
            return result;
        }

        try {
            List<AiMessage> messages = new ArrayList<>();
            messages.add(AiMessage.system(SYSTEM_PROMPT_EXTRACT_PARAMS));
            messages.add(AiMessage.user(userInput));

            AiClient client = aiClientFactory.createClient(config);
            AiChatResponse response = client.chat(AiChatRequest.of(config.getModel(), messages));
            if (!response.isSuccess()) {
                log.error("Extract query params failed: {}", response.getErrorMessage());
                return result;
            }

            JSONObject obj = JSON.parseObject(extractJson(response.getContent()));
            if (obj != null) {
                obj.forEach((k, v) -> {
                    if ("startTime".equalsIgnoreCase(k) || "endTime".equalsIgnoreCase(k)) {
                        return;
                    }
                    result.put(k, v != null ? v.toString() : "");
                });
            }
        } catch (Exception e) {
            log.error("Extract query params exception: {}", userInput, e);
        }
        return result;
    }

    /**
     * 优化通知内容（使用默认 AI 配置）
     */
    public NotificationContent optimizeNotification(String subject, String body, String context) {
        return optimizeNotification(subject, body, context, null);
    }

    /**
     * 优化通知内容，可指定 AI 配置，未指定或无效时回退到默认配置
     */
    public NotificationContent optimizeNotification(String subject, String body, String context, Long aiConfigId) {
        AiConfig config = aiConfigService.getEffectiveConfig(aiConfigId);
        if (config == null) {
            return new NotificationContent(subject, body);
        }

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("请优化以下通知内容。\n\n");
        userPrompt.append("原标题：").append(subject).append("\n\n");
        userPrompt.append("原正文：").append(body).append("\n\n");
        if (StringUtils.hasText(context)) {
            userPrompt.append("上下文：").append(context).append("\n\n");
        }
        userPrompt.append("请返回 JSON：{\"subject\":\"...\",\"body\":\"...\"}");

        List<AiMessage> messages = new ArrayList<>();
        messages.add(AiMessage.system(SYSTEM_PROMPT_NOTIFY));
        messages.add(AiMessage.user(userPrompt.toString()));

        AiClient client = aiClientFactory.createClient(config);
        AiChatResponse response = client.chat(AiChatRequest.of(config.getModel(), messages));
        if (!response.isSuccess()) {
            log.error("Optimize notification failed: {}", response.getErrorMessage());
            return new NotificationContent(subject, body);
        }

        return parseNotificationJson(response.getContent(), subject, body);
    }

    /**
     * 根据用户消息生成自然语言回复（用于非指令消息）
     */
    public String chatReply(String userInput) {
        AiConfig config = aiConfigService.getDefaultConfig();
        if (config == null) {
            return "未配置默认 AI，无法回复。";
        }

        List<AiMessage> messages = new ArrayList<>();
        String systemPrompt = StringUtils.hasText(config.getSystemPrompt())
                ? config.getSystemPrompt()
                : SYSTEM_PROMPT_CHAT;
        messages.add(AiMessage.system(systemPrompt));
        messages.add(AiMessage.user(userInput));

        AiClient client = aiClientFactory.createClient(config);
        AiChatResponse response = client.chat(AiChatRequest.of(config.getModel(), messages));
        if (!response.isSuccess()) {
            log.error("AI 闲聊回复失败: {}", response.getErrorMessage());
            return "AI 回复失败，请稍后再试。";
        }
        return response.getContent();
    }

    /**
     * 根据原始表结构生成结构化的数据字典文档
     */
    public String generateSchemaDoc(String rawSchema) {
        AiConfig config = aiConfigService.getDefaultConfig();
        if (config == null) {
            return rawSchema;
        }

        List<AiMessage> messages = new ArrayList<>();
        messages.add(AiMessage.system(SYSTEM_PROMPT_SCHEMA_DOC));
        messages.add(AiMessage.user(rawSchema));

        AiClient client = aiClientFactory.createClient(config);
        AiChatResponse response = client.chat(AiChatRequest.of(config.getModel(), messages));
        if (!response.isSuccess()) {
            log.error("Generate schema doc failed: {}", response.getErrorMessage());
            return rawSchema;
        }
        return response.getContent();
    }

    /**
     * 根据数据字典文档和用户问题生成 SQL（无历史上下文）
     */
    public SqlGenerateResult generateSql(String schemaDoc, String userQuestion) {
        return generateSql(schemaDoc, userQuestion, null);
    }

    /**
     * 根据数据字典文档、用户问题以及对话历史生成 SQL。
     * 历史上下文帮助理解指代、补充条件和延续前一次查询意图。
     */
    public SqlGenerateResult generateSql(String schemaDoc, String userQuestion, List<AiMessage> history) {
        return generateSql(schemaDoc, userQuestion, history, null);
    }

    /**
     * 根据数据字典文档、用户问题、对话历史以及数据源自定义 prompt 生成 SQL。
     * customPrompt 用于固化该数据源的业务口径、固定过滤条件（如 is_delete=0）、表/字段偏好与时间口径，
     * 会作为高优先级规则注入 system prompt，引导模型生成更贴合该数据源的 SQL。
     */
    public SqlGenerateResult generateSql(String schemaDoc, String userQuestion, List<AiMessage> history, String customPrompt) {
        AiConfig config = aiConfigService.getDefaultConfig();
        if (config == null) {
            return SqlGenerateResult.fail("未配置默认 AI");
        }

        List<AiMessage> messages = new ArrayList<>();
        // 仅保留一条 system 消息并置于首位（兼容 SenseNova 等要求 system 必须在开头的厂商），
        // 历史上下文以 user/assistant 轮次形式跟在用户消息之后，避免在 user 之后再插入 system。
        StringBuilder systemPrompt = new StringBuilder(SYSTEM_PROMPT_SQL_GENERATE);
        if (StringUtils.hasText(customPrompt)) {
            systemPrompt.append("\n\n【该数据源的自定义规则，请在生成 SQL 时严格遵循】\n")
                    .append(customPrompt.trim())
                    .append("\n注意：自定义规则用于明确业务口径与固定条件，不得突破上方通用约束中关于表/字段真实性、仅生成 SELECT 等硬性要求；"
                            + "当自定义规则指定了固定过滤（如 is_delete=0）或偏好表/字段时，必须在 SQL 中体现。");
        }
        boolean hasHistory = history != null && !history.isEmpty();
        if (hasHistory) {
            systemPrompt.append("\n\n另外，以下是与当前用户的连续对话记录，生成 SQL 时请结合上下文理解用户意图（如指代、补充条件、延续上次查询）。");
        }
        messages.add(AiMessage.system(systemPrompt.toString()));
        messages.add(AiMessage.user("数据库结构文档：\n" + schemaDoc));
        if (hasHistory) {
            messages.addAll(history);
        }
        messages.add(AiMessage.user("用户问题：" + userQuestion + "\n\n请生成 SQL。"));

        // 降低随机性，避免模型编造表/字段，提升对数据字典的遵循度
        AiChatRequest request = AiChatRequest.of(config.getModel(), messages);
        request.setTemperature(0.1);
        AiClient client = aiClientFactory.createClient(config);
        AiChatResponse response = client.chat(request);
        if (!response.isSuccess()) {
            log.error("Generate SQL failed: {}", response.getErrorMessage());
            return SqlGenerateResult.fail(response.getErrorMessage());
        }
        // 部分小模型在复杂问题下偶发返回空 content，重试一次以提高稳定性
        if (!StringUtils.hasText(response.getContent())) {
            log.warn("AI SQL 生成首次返回空内容，尝试重试一次");
            response = client.chat(request);
            if (!response.isSuccess()) {
                log.error("Generate SQL retry failed: {}", response.getErrorMessage());
                return SqlGenerateResult.fail(response.getErrorMessage());
            }
        }
        return parseSqlGenerateResult(response.getContent());
    }

    /**
     * 根据用户一句话生成系统配置
     */
    public NaturalConfigResult generateConfig(String userInput) {
        AiConfig config = aiConfigService.getDefaultConfig();
        if (config == null) {
            return new NaturalConfigResult("UNKNOWN", new JSONObject(), "未配置默认 AI");
        }

        List<AiMessage> messages = new ArrayList<>();
        messages.add(AiMessage.system(SYSTEM_PROMPT_NATURAL_CONFIG));
        messages.add(AiMessage.user(userInput));

        AiClient client = aiClientFactory.createClient(config);
        AiChatResponse response = client.chat(AiChatRequest.of(config.getModel(), messages));
        if (!response.isSuccess()) {
            log.error("Generate config failed: {}", response.getErrorMessage());
            return new NaturalConfigResult("UNKNOWN", new JSONObject(), response.getErrorMessage());
        }
        return parseNaturalConfigResult(response.getContent());
    }

    private SqlGenerateResult parseSqlGenerateResult(String content) {
        String json = extractJson(content);
        if (!StringUtils.hasText(json)) {
            log.warn("AI SQL 生成响应为空，原始内容: {}", truncate(content));
            return SqlGenerateResult.fail("AI 未返回有效的 SQL 生成结果（响应为空）");
        }
        try {
            JSONObject obj = JSON.parseObject(json);
            if (obj == null) {
                log.warn("AI SQL 生成响应非 JSON，原始内容: {}", truncate(content));
                return SqlGenerateResult.fail("AI 未返回有效的 SQL 生成结果（响应非 JSON）");
            }
            SqlGenerateResult result = new SqlGenerateResult();
            result.setSql(obj.getString("sql"));
            result.setExplanation(obj.getString("explanation"));
            result.setChartType(obj.getString("chartType"));
            result.setChartTitle(obj.getString("chartTitle"));
            JSONObject params = obj.getJSONObject("params");
            if (params != null) {
                params.forEach((k, v) -> result.getParams().put(k, v != null ? v.toString() : ""));
            }
            return result;
        } catch (Exception e) {
            log.warn("解析 AI SQL 生成结果失败，原始内容: {}", truncate(content), e);
            return SqlGenerateResult.fail("解析 SQL 结果失败");
        }
    }

    private NaturalConfigResult parseNaturalConfigResult(String content) {
        String json = extractJson(content);
        if (!StringUtils.hasText(json)) {
            log.warn("AI 配置生成响应为空，原始内容: {}", truncate(content));
            return new NaturalConfigResult("UNKNOWN", new JSONObject(), "AI 未返回有效结果");
        }
        try {
            JSONObject obj = JSON.parseObject(json);
            if (obj == null) {
                log.warn("AI 配置生成响应非 JSON，原始内容: {}", truncate(content));
                return new NaturalConfigResult("UNKNOWN", new JSONObject(), "AI 未返回有效结果");
            }
            String type = obj.getString("type");
            JSONObject config = obj.getJSONObject("config");
            String summary = obj.getString("summary");
            return new NaturalConfigResult(
                    StringUtils.hasText(type) ? type : "UNKNOWN",
                    config != null ? config : new JSONObject(),
                    StringUtils.hasText(summary) ? summary : ""
            );
        } catch (Exception e) {
            log.warn("解析 AI 配置生成结果失败，原始内容: {}", truncate(content), e);
            return new NaturalConfigResult("UNKNOWN", new JSONObject(), "解析配置结果失败");
        }
    }

    public record NaturalConfigResult(String type, JSONObject config, String summary) {
    }

    private IntentResult parseIntentJson(String content) {
        String json = extractJson(content);
        if (!StringUtils.hasText(json)) {
            log.warn("AI 意图识别响应为空，原始内容: {}", truncate(content));
            return unrecognized("AI 未返回有效结果");
        }
        IntentResult result = new IntentResult();
        try {
            JSONObject obj = JSON.parseObject(json);
            if (obj == null) {
                log.warn("AI 意图识别响应非 JSON，原始内容: {}", truncate(content));
                return unrecognized("AI 未返回有效结果");
            }
            result.setAction(obj.getString("action"));
            result.setSummary(obj.getString("summary"));
            JSONObject params = obj.getJSONObject("params");
            if (params != null) {
                params.forEach((k, v) -> result.getParams().put(k, v != null ? v.toString() : ""));
            }
            result.setRecognized(!"UNKNOWN".equalsIgnoreCase(result.getAction()));
        } catch (Exception e) {
            log.warn("解析 AI 意图识别结果失败，原始内容: {}", truncate(content), e);
            return unrecognized("解析失败");
        }
        return result;
    }

    private IntentResult unrecognized(String reason) {
        IntentResult result = new IntentResult();
        result.setAction("UNKNOWN");
        result.setSummary(reason);
        result.setRecognized(false);
        return result;
    }

    private NotificationContent parseNotificationJson(String content, String fallbackSubject, String fallbackBody) {
        String json = extractJson(content);
        if (!StringUtils.hasText(json)) {
            log.warn("AI 通知优化响应为空，原始内容: {}", truncate(content));
            return new NotificationContent(fallbackSubject, fallbackBody);
        }
        try {
            JSONObject obj = JSON.parseObject(json);
            if (obj == null) {
                log.warn("AI 通知优化响应非 JSON，原始内容: {}", truncate(content));
                return new NotificationContent(fallbackSubject, fallbackBody);
            }
            String subject = obj.getString("subject");
            String body = obj.getString("body");
            return new NotificationContent(
                    StringUtils.hasText(subject) ? subject : fallbackSubject,
                    StringUtils.hasText(body) ? body : fallbackBody
            );
        } catch (Exception e) {
            log.warn("解析 AI 通知优化结果失败，原始内容: {}", truncate(content), e);
            return new NotificationContent(fallbackSubject, fallbackBody);
        }
    }

    private String extractJson(String content) {
        if (content == null) {
            return "";
        }
        String trimmed = content.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceAll("^```(json)?\\s*", "").replaceAll("\\s*```$", "");
        }
        return trimmed.trim();
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 2000 ? value : value.substring(0, 2000) + "...(truncated)";
    }

    public record NotificationContent(String subject, String body) {
    }
}
