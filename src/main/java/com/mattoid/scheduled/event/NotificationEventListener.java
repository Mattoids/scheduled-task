package com.mattoid.scheduled.event;

import com.mattoid.scheduled.entity.*;
import com.mattoid.scheduled.service.*;
import com.mattoid.scheduled.service.wecom.WeComAppManager;
import com.mattoid.scheduled.service.wecom.WeComBotClient;
import com.mattoid.scheduled.storage.client.StorageClient;
import com.mattoid.scheduled.storage.service.StorageConfigService;
import com.mattoid.scheduled.util.PlaceholderUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class NotificationEventListener {

    private final NotificationRuleService notificationRuleService;
    private final NotificationConfigService notificationConfigService;
    private final EmailRecipientService emailRecipientService;
    private final EmailSenderService emailSenderService;
    private final WeComAppManager weComAppManager;
    private final WeComBotClient weComBotClient;
    private final AiAssistantService aiAssistantService;
    private final StorageConfigService storageConfigService;

    public NotificationEventListener(NotificationRuleService notificationRuleService,
                                     NotificationConfigService notificationConfigService,
                                     EmailRecipientService emailRecipientService,
                                     EmailSenderService emailSenderService,
                                     WeComAppManager weComAppManager,
                                     WeComBotClient weComBotClient,
                                     AiAssistantService aiAssistantService,
                                     StorageConfigService storageConfigService) {
        this.notificationRuleService = notificationRuleService;
        this.notificationConfigService = notificationConfigService;
        this.emailRecipientService = emailRecipientService;
        this.emailSenderService = emailSenderService;
        this.weComAppManager = weComAppManager;
        this.weComBotClient = weComBotClient;
        this.aiAssistantService = aiAssistantService;
        this.storageConfigService = storageConfigService;
    }

    @Async
    @EventListener
    public void onTaskExecutionEvent(TaskExecutionEvent event) {
        Long taskId = event.getTask() != null ? event.getTask().getId() : null;
        List<NotificationRule> rules = notificationRuleService.findEnabledByEventTypeAndTask(event.getEventType().name(), taskId);
        if (rules.isEmpty()) {
            return;
        }
        for (NotificationRule rule : rules) {
            try {
                dispatch(event, rule);
            } catch (Exception e) {
                log.error("通知发送失败: event={}, channel={}, configId={}",
                        event.getEventType(), rule.getChannel(), rule.getConfigId(), e);
            }
        }
    }

    private void dispatch(TaskExecutionEvent event, NotificationRule rule) throws Exception {
        switch (rule.getChannel()) {
            case "EMAIL" -> sendEmail(event, rule);
            case "WECOM_APP" -> sendWeComApp(event, rule);
            case "WECOM_BOT" -> sendWeComBot(event, rule);
            case "WECOM_INTELLIGENT_BOT" -> sendWeComIntelligentBot(event, rule);
            default -> log.warn("未知的通知渠道: {}", rule.getChannel());
        }
    }

    private void sendEmail(TaskExecutionEvent event, NotificationRule rule) throws Exception {
        if (rule.getConfigId() == null) {
            return;
        }
        NotificationConfig notificationConfig = notificationConfigService.getById(rule.getConfigId());
        if (notificationConfig == null || !"EMAIL".equals(notificationConfig.getConfigType()) ||
                notificationConfig.getStatus() == null || notificationConfig.getStatus() != 1) {
            log.warn("邮件配置不可用: {}", rule.getConfigId());
            return;
        }
        EmailConfig config = notificationConfigService.parseConfigJson(notificationConfig.getConfigJson(), EmailConfig.class);
        List<String> toList = resolveEmailRecipients(rule);
        if (toList.isEmpty()) {
            log.warn("邮件通知规则无收件人: ruleId={}", rule.getId());
            return;
        }
        Map<String, Object> data = buildInlineResultContext(event.getInlineResults());
        String subject = StringUtils.hasText(rule.getSubject())
                ? PlaceholderUtils.replacePlaceholders(rule.getSubject(), data)
                : buildDefaultSubject(event);
        String body = StringUtils.hasText(rule.getBody())
                ? PlaceholderUtils.replacePlaceholders(rule.getBody(), data)
                : buildDefaultSummary(event);

        if (rule.getAiOptimizeNotify() != null && rule.getAiOptimizeNotify() == 1) {
            AiAssistantService.NotificationContent optimized = optimizeNotify(rule, event, subject, body);
            subject = optimized.subject();
            body = optimized.body();
            log.info("通知规则 {} 已使用 AI 优化通知内容", rule.getId());
        }

        emailSenderService.sendEmail(config, toList, subject, body, event.getReportFiles());
    }

    private AiAssistantService.NotificationContent optimizeNotify(NotificationRule rule, TaskExecutionEvent event, String subject, String body) {
        String context = buildAiNotificationContext(event.getTask(), event);
        return aiAssistantService.optimizeNotification(subject, body, context, rule.getAiConfigId());
    }

    private String buildAiNotificationContext(TaskConfig task, TaskExecutionEvent event) {
        List<File> reportFiles = event.getReportFiles();
        List<InlineSqlResult> inlineResults = event.getInlineResults();
        StringBuilder sb = new StringBuilder();
        sb.append("任务名称: ").append(task.getTaskName()).append("\n");
        sb.append("任务编码: ").append(task.getTaskCode()).append("\n");
        sb.append("触发类型: ").append(task.getTriggerType()).append("\n");
        sb.append("附件数量: ").append(reportFiles != null ? reportFiles.size() : 0).append("\n");
        if (reportFiles != null && !reportFiles.isEmpty()) {
            sb.append("附件名称: ");
            for (int i = 0; i < reportFiles.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(reportFiles.get(i).getName());
            }
            sb.append("\n");
        }
        sb.append("内联 SQL 结果数量: ").append(inlineResults != null ? inlineResults.size() : 0).append("\n");
        if (inlineResults != null) {
            for (InlineSqlResult result : inlineResults) {
                sb.append("  - ").append(result.sqlName())
                        .append("(")
                        .append(result.sqlCode())
                        .append("): ")
                        .append(result.data().size())
                        .append(" 行\n");
            }
        }
        return sb.toString();
    }

    private List<String> resolveEmailRecipients(NotificationRule rule) {
        Set<String> emails = new LinkedHashSet<>();
        List<EmailRecipient> individuals = emailRecipientService.listByIds(rule.getRecipientIds());
        for (EmailRecipient r : individuals) {
            if (StringUtils.hasText(r.getEmail())) {
                emails.add(r.getEmail());
            }
        }
        List<EmailRecipient> groups = emailRecipientService.listByGroupIds(rule.getRecipientGroupIds());
        for (EmailRecipient r : groups) {
            if (StringUtils.hasText(r.getEmail())) {
                emails.add(r.getEmail());
            }
        }
        return new ArrayList<>(emails);
    }

    private void sendWeComApp(TaskExecutionEvent event, NotificationRule rule) throws Exception {
        if (rule.getConfigId() == null) {
            return;
        }
        NotificationConfig notificationConfig = notificationConfigService.getById(rule.getConfigId());
        if (notificationConfig == null || !"WECOM_APP".equals(notificationConfig.getConfigType()) ||
                notificationConfig.getStatus() == null || notificationConfig.getStatus() != 1) {
            log.warn("企业微信应用配置不可用: {}", rule.getConfigId());
            return;
        }
        Map<String, Object> data = buildInlineResultContext(event.getInlineResults());
        String toUser = StringUtils.hasText(rule.getWecomToUser()) ? rule.getWecomToUser() : "@all";
        String content = StringUtils.hasText(rule.getContent())
                ? PlaceholderUtils.replacePlaceholders(rule.getContent(), data)
                : buildDefaultSummary(event);

        if (rule.getAiOptimizeNotify() != null && rule.getAiOptimizeNotify() == 1) {
            AiAssistantService.NotificationContent optimized = optimizeNotify(rule, event,
                    StringUtils.hasText(rule.getSubject()) ? rule.getSubject() : event.getTask().getTaskName(),
                    content);
            content = optimized.body();
            log.info("通知规则 {} 已使用 AI 优化通知内容", rule.getId());
        }

        weComAppManager.sendText(rule.getConfigId(), toUser, content);
        List<File> reportFiles = event.getReportFiles();
        if (rule.getStorageConfigId() != null && !reportFiles.isEmpty()) {
            List<String> urls = uploadReportFilesToStorage(rule.getStorageConfigId(), reportFiles);
            if (!urls.isEmpty()) {
                String urlContent = "文件下载地址：\n" + String.join("\n", urls);
                weComAppManager.sendText(rule.getConfigId(), toUser, urlContent);
            }
        } else {
            for (File file : reportFiles) {
                weComAppManager.sendFile(rule.getConfigId(), toUser, file);
            }
        }
    }

    private void sendWeComBot(TaskExecutionEvent event, NotificationRule rule) throws Exception {
        sendWeComBot(event, rule, "WECOM_BOT");
    }

    private void sendWeComIntelligentBot(TaskExecutionEvent event, NotificationRule rule) throws Exception {
        sendWeComBot(event, rule, "WECOM_INTELLIGENT_BOT");
    }

    private void sendWeComBot(TaskExecutionEvent event, NotificationRule rule, String configType) throws Exception {
        if (rule.getConfigId() == null) {
            return;
        }
        NotificationConfig notificationConfig = notificationConfigService.getById(rule.getConfigId());
        if (notificationConfig == null || !configType.equals(notificationConfig.getConfigType()) ||
                notificationConfig.getStatus() == null || notificationConfig.getStatus() != 1) {
            log.warn("{} 配置不可用: {}", configType, rule.getConfigId());
            return;
        }
        Map<String, Object> data = buildInlineResultContext(event.getInlineResults());
        WeComBotConfig config = notificationConfigService.parseConfigJson(notificationConfig.getConfigJson(), WeComBotConfig.class);
        String content = StringUtils.hasText(rule.getContent())
                ? PlaceholderUtils.replacePlaceholders(rule.getContent(), data)
                : buildDefaultSummary(event);

        if (rule.getAiOptimizeNotify() != null && rule.getAiOptimizeNotify() == 1) {
            AiAssistantService.NotificationContent optimized = optimizeNotify(rule, event,
                    StringUtils.hasText(rule.getSubject()) ? rule.getSubject() : event.getTask().getTaskName(),
                    content);
            content = optimized.body();
            log.info("通知规则 {} 已使用 AI 优化通知内容", rule.getId());
        }

        List<String> mentionedList = parseMentionedList(rule.getWecomToUser());
        weComBotClient.sendText(config.getWebhookKey(), content, mentionedList);
        List<File> reportFiles = event.getReportFiles();
        if (rule.getStorageConfigId() != null && !reportFiles.isEmpty()) {
            List<String> urls = uploadReportFilesToStorage(rule.getStorageConfigId(), reportFiles);
            if (!urls.isEmpty()) {
                String urlContent = "文件下载地址：\n" + String.join("\n", urls);
                weComBotClient.sendText(config.getWebhookKey(), urlContent, mentionedList);
            }
        } else {
            for (File file : reportFiles) {
                weComBotClient.sendFile(config.getWebhookKey(), file);
            }
        }
    }

    private List<String> parseMentionedList(String wecomToUser) {
        if (!StringUtils.hasText(wecomToUser)) {
            return Collections.emptyList();
        }
        return Arrays.stream(wecomToUser.split("\\|"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
    }

    private List<String> uploadReportFilesToStorage(Long storageConfigId, List<File> reportFiles) {
        List<String> urls = new ArrayList<>();
        if (storageConfigId == null || reportFiles == null || reportFiles.isEmpty()) {
            return urls;
        }
        try {
            StorageClient client = storageConfigService.getClient(storageConfigId);
            for (File file : reportFiles) {
                if (file == null || !file.exists()) {
                    continue;
                }
                String url = client.upload(file, file.getName());
                urls.add(url);
                log.info("文件已上传至存储系统: configId={}, file={}, url={}", storageConfigId, file.getName(), url);
            }
        } catch (Exception e) {
            log.error("上传文件到存储系统失败: configId={}", storageConfigId, e);
        }
        return urls;
    }

    private Map<String, Object> buildInlineResultContext(List<InlineSqlResult> inlineResults) {
        if (inlineResults == null || inlineResults.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> context = new LinkedHashMap<>();
        for (InlineSqlResult result : inlineResults) {
            if (result.data() == null || result.data().isEmpty()) {
                continue;
            }
            List<Map<String, Object>> rows = result.data();
            Set<String> columnNames = new LinkedHashSet<>();
            for (Map<String, Object> row : rows) {
                columnNames.addAll(row.keySet());
            }
            for (String col : columnNames) {
                List<Object> values = new ArrayList<>();
                for (Map<String, Object> row : rows) {
                    values.add(row.get(col));
                }
                // If single row and single column, use plain value; otherwise use list
                if (rows.size() == 1 && columnNames.size() == 1) {
                    context.put(col, values.get(0));
                } else {
                    context.put(col, values);
                }
            }
            // Also add total row count for the SQL
            context.put(result.sqlName() + "_count", rows.size());
        }
        return context;
    }

    private String buildDefaultSubject(TaskExecutionEvent event) {
        return "任务执行通知 - " + event.getTask().getTaskName();
    }

    private String buildDefaultSummary(TaskExecutionEvent event) {
        TaskConfig task = event.getTask();
        TaskLog log = event.getTaskLog();
        StringBuilder sb = new StringBuilder();
        sb.append("任务执行通知\n");
        sb.append("任务: ").append(task.getTaskName()).append("\n");
        sb.append("状态: ").append(log.getStatus()).append("\n");
        if (log.getStartTime() != null && log.getEndTime() != null) {
            long seconds = java.time.Duration.between(log.getStartTime(), log.getEndTime()).getSeconds();
            sb.append("耗时: ").append(seconds).append("s\n");
        }
        if (StringUtils.hasText(log.getResultMessage())) {
            sb.append("结果: ").append(log.getResultMessage()).append("\n");
        }
        if (StringUtils.hasText(log.getErrorMessage())) {
            sb.append("错误: ").append(log.getErrorMessage()).append("\n");
        }
        if (StringUtils.hasText(log.getFilePath())) {
            sb.append("文件: ").append(log.getFilePath());
        }
        return sb.toString();
    }
}
