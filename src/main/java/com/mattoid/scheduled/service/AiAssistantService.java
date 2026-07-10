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
            - QUERY_DATA: 查询 SQL 数据。参数：keyword(数据主题关键词，如 道达尔渠道数据)、chartType(可选：line/bar/pie)、timeRange(时间描述，如 上个月)、channel(渠道/品牌等)
            - UNKNOWN: 无法识别
            返回格式：{"action":"VIEW_TASKS","params":{"keyword":"门店"},"summary":"查看与门店相关的任务"}
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
            """;

    private static final String SYSTEM_PROMPT_SQL_GENERATE = """
            你是一名 SQL 专家。请根据提供的数据库结构文档以及用户的自然语言问题，生成一条可执行的 SQL 查询语句。
            只返回 JSON 格式结果，不要包含任何解释。
            返回格式：{"sql":"SELECT ...","params":{},"explanation":"简要说明"}
            注意：
            1. 仅生成 SELECT 查询，禁止生成 INSERT/UPDATE/DELETE/DROP/ALTER 等变更语句。
            2. SQL 中的参数请使用 ${name} 占位符，并在 params 中提供默认值。
            3. 如果无法生成，请返回 {"sql":"","params":{},"explanation":"无法生成 SQL"}。
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
     * 根据数据字典文档和用户问题生成 SQL
     */
    public SqlGenerateResult generateSql(String schemaDoc, String userQuestion) {
        AiConfig config = aiConfigService.getDefaultConfig();
        if (config == null) {
            return SqlGenerateResult.fail("未配置默认 AI");
        }

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("数据库结构文档：\n").append(schemaDoc).append("\n\n");
        userPrompt.append("用户问题：").append(userQuestion).append("\n\n");
        userPrompt.append("请生成 SQL。");

        List<AiMessage> messages = new ArrayList<>();
        messages.add(AiMessage.system(SYSTEM_PROMPT_SQL_GENERATE));
        messages.add(AiMessage.user(userPrompt.toString()));

        AiClient client = aiClientFactory.createClient(config);
        AiChatResponse response = client.chat(AiChatRequest.of(config.getModel(), messages));
        if (!response.isSuccess()) {
            log.error("Generate SQL failed: {}", response.getErrorMessage());
            return SqlGenerateResult.fail(response.getErrorMessage());
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
        try {
            String json = extractJson(content);
            JSONObject obj = JSON.parseObject(json);
            SqlGenerateResult result = new SqlGenerateResult();
            result.setSql(obj.getString("sql"));
            result.setExplanation(obj.getString("explanation"));
            JSONObject params = obj.getJSONObject("params");
            if (params != null) {
                params.forEach((k, v) -> result.getParams().put(k, v != null ? v.toString() : ""));
            }
            return result;
        } catch (Exception e) {
            log.error("Parse SQL generate result failed: {}", content, e);
            return SqlGenerateResult.fail("解析 SQL 结果失败");
        }
    }

    private NaturalConfigResult parseNaturalConfigResult(String content) {
        try {
            String json = extractJson(content);
            JSONObject obj = JSON.parseObject(json);
            String type = obj.getString("type");
            JSONObject config = obj.getJSONObject("config");
            String summary = obj.getString("summary");
            return new NaturalConfigResult(
                    StringUtils.hasText(type) ? type : "UNKNOWN",
                    config != null ? config : new JSONObject(),
                    StringUtils.hasText(summary) ? summary : ""
            );
        } catch (Exception e) {
            log.error("Parse natural config result failed: {}", content, e);
            return new NaturalConfigResult("UNKNOWN", new JSONObject(), "解析配置结果失败");
        }
    }

    public record NaturalConfigResult(String type, JSONObject config, String summary) {
    }

    private IntentResult parseIntentJson(String content) {
        IntentResult result = new IntentResult();
        try {
            String json = extractJson(content);
            JSONObject obj = JSON.parseObject(json);
            result.setAction(obj.getString("action"));
            result.setSummary(obj.getString("summary"));
            JSONObject params = obj.getJSONObject("params");
            if (params != null) {
                params.forEach((k, v) -> result.getParams().put(k, v != null ? v.toString() : ""));
            }
            result.setRecognized(!"UNKNOWN".equalsIgnoreCase(result.getAction()));
        } catch (Exception e) {
            log.error("Parse intent json failed: {}", content, e);
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
        try {
            String json = extractJson(content);
            JSONObject obj = JSON.parseObject(json);
            String subject = obj.getString("subject");
            String body = obj.getString("body");
            return new NotificationContent(
                    StringUtils.hasText(subject) ? subject : fallbackSubject,
                    StringUtils.hasText(body) ? body : fallbackBody
            );
        } catch (Exception e) {
            log.error("Parse notification json failed: {}", content, e);
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

    public record NotificationContent(String subject, String body) {
    }
}
