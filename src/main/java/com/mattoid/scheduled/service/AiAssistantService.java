package com.mattoid.scheduled.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.mattoid.scheduled.ai.AiChatRequest;
import com.mattoid.scheduled.ai.AiChatResponse;
import com.mattoid.scheduled.ai.AiClient;
import com.mattoid.scheduled.ai.AiClientFactory;
import com.mattoid.scheduled.ai.AiMessage;
import com.mattoid.scheduled.dto.IntentResult;
import com.mattoid.scheduled.entity.AiConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class AiAssistantService {

    private static final String SYSTEM_PROMPT_INTENT = """
            你是一个智能报表系统助手。请理解用户输入，并只返回 JSON 格式结果，不要包含任何解释。
            可用的 action 及参数如下：
            - VIEW_TASKS: 查看任务列表。参数：keyword(可选关键词)、status(ENABLE/DISABLE)
            - TRIGGER_TASK: 手动触发任务。参数：taskId 或 taskName
            - VIEW_LOGS: 查看执行日志。参数：taskId、status(SUCCESS/FAILED/RUNNING)、date(如 today/yesterday/2024-01-01)
            - CREATE_TASK: 创建任务。参数：taskName、triggerType(CRON/ONCE)、triggerConfig
            - UNKNOWN: 无法识别
            返回格式：{"action":"VIEW_TASKS","params":{"keyword":"门店"},"summary":"查看与门店相关的任务"}
            """;

    private static final String SYSTEM_PROMPT_NOTIFY = """
            你是一名专业的商务沟通助手。请根据用户提供的通知标题和正文，优化表达，使其：
            1. 简洁清晰，重点突出；
            2. 语气专业、礼貌；
            3. 保留关键数据、变量占位符（如 {lastMonth}、{yyyyMMdd}）不变；
            4. 邮件正文支持 HTML，可适当使用段落、加粗、列表提升可读性；
            5. 只返回优化后的 JSON 结果，不要解释。
            返回格式：{"subject":"优化后的标题","body":"优化后的正文"}
            """;

    private static final String SYSTEM_PROMPT_CHAT = """
            你是企业微信智能助手，服务于定时任务报表系统。
            请根据用户消息给出简洁、友好、专业的中文回复。
            如果用户询问系统功能，可提示可用指令，例如：帮助、任务列表、运行 {任务ID}、任务日志 {任务ID}。
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
