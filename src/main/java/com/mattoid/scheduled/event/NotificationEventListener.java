package com.mattoid.scheduled.event;

import com.mattoid.scheduled.entity.*;
import com.mattoid.scheduled.service.*;
import com.mattoid.scheduled.service.wecom.WeComAppManager;
import com.mattoid.scheduled.service.wecom.WeComBotClient;
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

    public NotificationEventListener(NotificationRuleService notificationRuleService,
                                     NotificationConfigService notificationConfigService,
                                     EmailRecipientService emailRecipientService,
                                     EmailSenderService emailSenderService,
                                     WeComAppManager weComAppManager,
                                     WeComBotClient weComBotClient,
                                     AiAssistantService aiAssistantService) {
        this.notificationRuleService = notificationRuleService;
        this.notificationConfigService = notificationConfigService;
        this.emailRecipientService = emailRecipientService;
        this.emailSenderService = emailSenderService;
        this.weComAppManager = weComAppManager;
        this.weComBotClient = weComBotClient;
        this.aiAssistantService = aiAssistantService;
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
        String subject = StringUtils.hasText(rule.getSubject())
                ? PlaceholderUtils.replacePlaceholders(rule.getSubject())
                : buildDefaultSubject(event);
        String body = StringUtils.hasText(rule.getBody())
                ? PlaceholderUtils.replacePlaceholders(rule.getBody())
                : buildDefaultSummary(event);

        if (rule.getAiOptimizeNotify() != null && rule.getAiOptimizeNotify() == 1) {
            String context = buildAiNotificationContext(event.getTask(), event.getReportFiles());
            AiAssistantService.NotificationContent optimized = aiAssistantService.optimizeNotification(subject, body, context, rule.getAiConfigId());
            subject = optimized.subject();
            body = optimized.body();
            log.info("通知规则 {} 已使用 AI 优化邮件通知内容", rule.getId());
        }

        emailSenderService.sendEmail(config, toList, subject, body, event.getReportFiles());
    }

    private String buildAiNotificationContext(TaskConfig task, List<File> reportFiles) {
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
        String toUser = StringUtils.hasText(rule.getWecomToUser()) ? rule.getWecomToUser() : "@all";
        String content = StringUtils.hasText(rule.getContent())
                ? PlaceholderUtils.replacePlaceholders(rule.getContent())
                : buildDefaultSummary(event);
        weComAppManager.sendText(rule.getConfigId(), toUser, content);
        for (File file : event.getReportFiles()) {
            weComAppManager.sendFile(rule.getConfigId(), toUser, file);
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
        WeComBotConfig config = notificationConfigService.parseConfigJson(notificationConfig.getConfigJson(), WeComBotConfig.class);
        String content = StringUtils.hasText(rule.getContent())
                ? PlaceholderUtils.replacePlaceholders(rule.getContent())
                : buildDefaultSummary(event);
        List<String> mentionedList = parseMentionedList(rule.getWecomToUser());
        weComBotClient.sendText(config.getWebhookKey(), content, mentionedList);
        for (File file : event.getReportFiles()) {
            weComBotClient.sendFile(config.getWebhookKey(), file);
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
