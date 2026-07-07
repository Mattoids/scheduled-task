package com.mattoid.scheduled.event;

import com.mattoid.scheduled.entity.*;
import com.mattoid.scheduled.service.*;
import com.mattoid.scheduled.service.notify.DingTalkClient;
import com.mattoid.scheduled.service.notify.FeishuClient;
import com.mattoid.scheduled.service.notify.SlackClient;
import com.mattoid.scheduled.service.notify.WebhookClient;
import com.mattoid.scheduled.service.wecom.WeComAppManager;
import com.mattoid.scheduled.service.wecom.WeComBotClient;
import com.mattoid.scheduled.service.wecom.WeComIntelligentBotClient;
import com.mattoid.scheduled.storage.client.StorageClient;
import com.mattoid.scheduled.storage.service.StorageConfigService;
import com.mattoid.scheduled.util.CryptoUtil;
import com.mattoid.scheduled.util.HtmlToMarkdownConverter;
import com.mattoid.scheduled.util.MarkdownUtils;
import com.mattoid.scheduled.util.PlaceholderUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.File;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Component
public class NotificationEventListener {

    private static final int MAX_RETRY = 2;
    private static final long RETRY_DELAY_MS = 1000;

    private final NotificationRuleService notificationRuleService;
    private final NotificationConfigService notificationConfigService;
    private final NotificationLogService notificationLogService;
    private final EmailRecipientService emailRecipientService;
    private final EmailSenderService emailSenderService;
    private final WeComAppManager weComAppManager;
    private final WeComBotClient weComBotClient;
    private final WeComIntelligentBotClient weComIntelligentBotClient;
    private final DingTalkClient dingTalkClient;
    private final FeishuClient feishuClient;
    private final SlackClient slackClient;
    private final WebhookClient webhookClient;
    private final AiAssistantService aiAssistantService;
    private final StorageConfigService storageConfigService;

    public NotificationEventListener(NotificationRuleService notificationRuleService,
                                     NotificationConfigService notificationConfigService,
                                     NotificationLogService notificationLogService,
                                     EmailRecipientService emailRecipientService,
                                     EmailSenderService emailSenderService,
                                     WeComAppManager weComAppManager,
                                     WeComBotClient weComBotClient,
                                     WeComIntelligentBotClient weComIntelligentBotClient,
                                     DingTalkClient dingTalkClient,
                                     FeishuClient feishuClient,
                                     SlackClient slackClient,
                                     WebhookClient webhookClient,
                                     AiAssistantService aiAssistantService,
                                     StorageConfigService storageConfigService) {
        this.notificationRuleService = notificationRuleService;
        this.notificationConfigService = notificationConfigService;
        this.notificationLogService = notificationLogService;
        this.emailRecipientService = emailRecipientService;
        this.emailSenderService = emailSenderService;
        this.weComAppManager = weComAppManager;
        this.weComBotClient = weComBotClient;
        this.weComIntelligentBotClient = weComIntelligentBotClient;
        this.dingTalkClient = dingTalkClient;
        this.feishuClient = feishuClient;
        this.slackClient = slackClient;
        this.webhookClient = webhookClient;
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
            dispatchWithRetry(event, rule);
        }
    }

    private void dispatchWithRetry(TaskExecutionEvent event, NotificationRule rule) {
        String content = buildDefaultSummary(event);
        for (int attempt = 0; attempt <= MAX_RETRY; attempt++) {
            NotificationLog notificationLog = createLog(event, rule, content, attempt);
            try {
                dispatch(event, rule);
                notificationLog.setStatus("SUCCESS");
                notificationLogService.save(notificationLog);
                return;
            } catch (Exception e) {
                notificationLog.setStatus("FAILED");
                notificationLog.setErrorMessage(truncate(e.getMessage(), 1000));
                notificationLogService.save(notificationLog);
                log.error("通知发送失败(尝试 {}/{}): event={}, channel={}, configId={}, ruleId={}",
                        attempt + 1, MAX_RETRY + 1, event.getEventType(), rule.getChannel(), rule.getConfigId(), rule.getId(), e);
                if (attempt < MAX_RETRY) {
                    try {
                        Thread.sleep(RETRY_DELAY_MS * (attempt + 1));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }

    private NotificationLog createLog(TaskExecutionEvent event, NotificationRule rule, String content, int attempt) {
        NotificationLog log = new NotificationLog();
        log.setTaskId(event.getTask() != null ? event.getTask().getId() : null);
        log.setEventType(event.getEventType() != null ? event.getEventType().name() : null);
        log.setRuleId(rule.getId());
        log.setChannel(rule.getChannel());
        log.setConfigId(rule.getConfigId());
        log.setRecipient(resolveRecipient(rule));
        log.setContent(truncate(content, 2000));
        log.setRetryCount(attempt);
        return log;
    }

    private String resolveRecipient(NotificationRule rule) {
        if ("EMAIL".equals(rule.getChannel())) {
            return StringUtils.hasText(rule.getRecipientIds()) ? rule.getRecipientIds() : rule.getRecipientGroupIds();
        }
        return StringUtils.hasText(rule.getWecomToUser()) ? rule.getWecomToUser() : "@all";
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }

    private void dispatch(TaskExecutionEvent event, NotificationRule rule) throws Exception {
        switch (rule.getChannel()) {
            case "EMAIL" -> sendEmail(event, rule);
            case "WECOM_APP" -> sendWeComApp(event, rule);
            case "WECOM_BOT" -> sendWeComBot(event, rule);
            case "WECOM_INTELLIGENT_BOT" -> sendWeComIntelligentBot(event, rule);
            case "DINGTALK" -> sendDingTalk(event, rule);
            case "FEISHU" -> sendFeishu(event, rule);
            case "SLACK" -> sendSlack(event, rule);
            case "WEBHOOK" -> sendWebhook(event, rule);
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

        Map<String, File> inlineImages = new LinkedHashMap<>();
        body = replaceChartPlaceholdersForEmail(body, event.getChartFiles(), inlineImages);

        emailSenderService.sendEmail(config, toList, subject, body, event.getReportFiles(), inlineImages);
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

        content = HtmlToMarkdownConverter.convert(content);
        content = MarkdownUtils.toPlainText(content);

        ChartPlaceholderResult chartResult = replaceChartPlaceholdersForWeCom(content, event.getChartFiles());
        content = chartResult.content();

        weComAppManager.sendText(rule.getConfigId(), toUser, formatWeComText(content, event.getTask().getTaskName()));

        for (File imageFile : chartResult.images()) {
            weComAppManager.sendImage(rule.getConfigId(), toUser, imageFile);
        }

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
        if (rule.getConfigId() == null) {
            return;
        }
        NotificationConfig notificationConfig = notificationConfigService.getById(rule.getConfigId());
        if (notificationConfig == null || !"WECOM_INTELLIGENT_BOT".equals(notificationConfig.getConfigType()) ||
                notificationConfig.getStatus() == null || notificationConfig.getStatus() != 1) {
            log.warn("智能机器人配置不可用: {}", rule.getConfigId());
            return;
        }
        WeComIntelligentBotConfig config = notificationConfigService.parseConfigJson(notificationConfig.getConfigJson(), WeComIntelligentBotConfig.class);
        String mode = StringUtils.hasText(config.getMode()) ? config.getMode() : "LONGCHAIN";

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

        content = HtmlToMarkdownConverter.convert(content);

        if ("CALLBACK".equals(mode)) {
            String textContent = MarkdownUtils.toPlainText(content);

            ChartPlaceholderResult chartResult = replaceChartPlaceholdersForWeCom(textContent, event.getChartFiles());
            textContent = chartResult.content();

            weComAppManager.sendText(rule.getConfigId(), toUser, formatWeComText(textContent, event.getTask().getTaskName()));

            for (File imageFile : chartResult.images()) {
                weComAppManager.sendImage(rule.getConfigId(), toUser, imageFile);
            }

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
        } else {
            // 长链模式仅支持被动回复用户消息，无法主动推送任务通知
            log.warn("智能机器人长链模式不支持主动推送通知，请将通知规则 {} 切换到 CALLBACK 模式", rule.getId());
        }
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

        content = HtmlToMarkdownConverter.convert(content);

        ChartPlaceholderResult chartResult = replaceChartPlaceholdersForWeCom(content, event.getChartFiles());
        content = chartResult.content();

        List<String> mentionedList = parseMentionedList(rule.getWecomToUser());
        weComBotClient.sendMarkdown(config.getWebhookKey(), formatWeComMarkdown(content, event.getTask().getTaskName()));

        for (File imageFile : chartResult.images()) {
            weComBotClient.sendImage(config.getWebhookKey(), imageFile);
        }

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

    private void sendDingTalk(TaskExecutionEvent event, NotificationRule rule) throws Exception {
        if (rule.getConfigId() == null) {
            return;
        }
        NotificationConfig notificationConfig = notificationConfigService.getById(rule.getConfigId());
        if (notificationConfig == null || !"DINGTALK".equals(notificationConfig.getConfigType()) ||
                notificationConfig.getStatus() == null || notificationConfig.getStatus() != 1) {
            log.warn("钉钉配置不可用: {}", rule.getConfigId());
            return;
        }
        DingTalkConfig config = notificationConfigService.parseConfigJson(notificationConfig.getConfigJson(), DingTalkConfig.class);
        String content = resolveNotifyContent(event, rule);
        List<String> atMobiles = parseMentionedList(rule.getWecomToUser());
        dingTalkClient.sendMarkdown(
                config.getWebhookUrl(),
                CryptoUtil.decryptIfNeeded(config.getSecret()),
                event.getTask().getTaskName(),
                formatWeComMarkdown(content, event.getTask().getTaskName()),
                atMobiles,
                config.getAtAll());
        sendReportFileLinks(event, rule, (urls) -> {
            try {
                dingTalkClient.sendText(
                        config.getWebhookUrl(),
                        CryptoUtil.decryptIfNeeded(config.getSecret()),
                        "文件下载地址：\n" + String.join("\n", urls),
                        null,
                        false);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void sendFeishu(TaskExecutionEvent event, NotificationRule rule) throws Exception {
        if (rule.getConfigId() == null) {
            return;
        }
        NotificationConfig notificationConfig = notificationConfigService.getById(rule.getConfigId());
        if (notificationConfig == null || !"FEISHU".equals(notificationConfig.getConfigType()) ||
                notificationConfig.getStatus() == null || notificationConfig.getStatus() != 1) {
            log.warn("飞书配置不可用: {}", rule.getConfigId());
            return;
        }
        FeishuConfig config = notificationConfigService.parseConfigJson(notificationConfig.getConfigJson(), FeishuConfig.class);
        String content = resolveNotifyContent(event, rule);
        feishuClient.sendText(
                config.getWebhookUrl(),
                CryptoUtil.decryptIfNeeded(config.getSecret()),
                formatWeComMarkdown(content, event.getTask().getTaskName()));
        sendReportFileLinks(event, rule, (urls) -> {
            try {
                feishuClient.sendText(
                        config.getWebhookUrl(),
                        CryptoUtil.decryptIfNeeded(config.getSecret()),
                        "文件下载地址：\n" + String.join("\n", urls));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void sendSlack(TaskExecutionEvent event, NotificationRule rule) throws Exception {
        if (rule.getConfigId() == null) {
            return;
        }
        NotificationConfig notificationConfig = notificationConfigService.getById(rule.getConfigId());
        if (notificationConfig == null || !"SLACK".equals(notificationConfig.getConfigType()) ||
                notificationConfig.getStatus() == null || notificationConfig.getStatus() != 1) {
            log.warn("Slack 配置不可用: {}", rule.getConfigId());
            return;
        }
        SlackConfig config = notificationConfigService.parseConfigJson(notificationConfig.getConfigJson(), SlackConfig.class);
        String content = resolveNotifyContent(event, rule);
        slackClient.sendText(
                CryptoUtil.decryptIfNeeded(config.getWebhookUrl()),
                formatWeComMarkdown(content, event.getTask().getTaskName()),
                config.getChannel(),
                config.getUsername());
    }

    private void sendWebhook(TaskExecutionEvent event, NotificationRule rule) throws Exception {
        if (rule.getConfigId() == null) {
            return;
        }
        NotificationConfig notificationConfig = notificationConfigService.getById(rule.getConfigId());
        if (notificationConfig == null || !"WEBHOOK".equals(notificationConfig.getConfigType()) ||
                notificationConfig.getStatus() == null || notificationConfig.getStatus() != 1) {
            log.warn("Webhook 配置不可用: {}", rule.getConfigId());
            return;
        }
        WebhookConfig config = notificationConfigService.parseConfigJson(notificationConfig.getConfigJson(), WebhookConfig.class);
        String content = resolveNotifyContent(event, rule);
        Map<String, Object> placeholders = new HashMap<>(webhookClient.buildPlaceholders(event.getTask().getTaskName(), content));
        placeholders.putAll(buildInlineResultContext(event.getInlineResults()));

        Map<String, String> headers = decryptWebhookHeaders(config.getHeaders());
        webhookClient.send(
                CryptoUtil.decryptIfNeeded(config.getUrl()),
                config.getMethod(),
                headers,
                config.getBodyTemplate(),
                placeholders,
                config.getTimeoutSeconds());
    }

    private String resolveNotifyContent(TaskExecutionEvent event, NotificationRule rule) throws Exception {
        Map<String, Object> data = buildInlineResultContext(event.getInlineResults());
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

        content = HtmlToMarkdownConverter.convert(content);
        return MarkdownUtils.toPlainText(content);
    }

    private Map<String, String> decryptWebhookHeaders(Map<String, String> headers) {
        if (headers == null) {
            return null;
        }
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            result.put(entry.getKey(), CryptoUtil.decryptIfNeeded(entry.getValue()));
        }
        return result;
    }

    private void sendReportFileLinks(TaskExecutionEvent event, NotificationRule rule, java.util.function.Consumer<List<String>> sender) {
        List<File> reportFiles = event.getReportFiles();
        if (rule.getStorageConfigId() != null && reportFiles != null && !reportFiles.isEmpty()) {
            List<String> urls = uploadReportFilesToStorage(rule.getStorageConfigId(), reportFiles);
            if (!urls.isEmpty()) {
                sender.accept(urls);
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

    private static final Pattern CHART_PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{chart:([^}]+)\\}");

    private String replaceChartPlaceholdersForEmail(String content, Map<String, File> chartFiles, Map<String, File> inlineImages) {
        if (!StringUtils.hasText(content)) {
            return content;
        }
        Matcher matcher = CHART_PLACEHOLDER_PATTERN.matcher(content);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String sqlCode = matcher.group(1).trim();
            File chartFile = chartFiles != null ? chartFiles.get(sqlCode) : null;
            if (chartFile != null && chartFile.exists()) {
                String cid = "chart_" + sqlCode;
                inlineImages.put(cid, chartFile);
                matcher.appendReplacement(sb, "<img src=\"cid:" + Matcher.quoteReplacement(cid) + "\" />");
            } else {
                matcher.appendReplacement(sb, "[图表未生成: " + Matcher.quoteReplacement(sqlCode) + "]");
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private ChartPlaceholderResult replaceChartPlaceholdersForWeCom(String content, Map<String, File> chartFiles) {
        if (!StringUtils.hasText(content)) {
            return new ChartPlaceholderResult(content, Collections.emptyList());
        }
        List<File> images = new ArrayList<>();
        Matcher matcher = CHART_PLACEHOLDER_PATTERN.matcher(content);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String sqlCode = matcher.group(1).trim();
            File chartFile = chartFiles != null ? chartFiles.get(sqlCode) : null;
            if (chartFile != null && chartFile.exists()) {
                images.add(chartFile);
                matcher.appendReplacement(sb, "[图表: " + Matcher.quoteReplacement(sqlCode) + "]");
            } else {
                matcher.appendReplacement(sb, "[图表未生成: " + Matcher.quoteReplacement(sqlCode) + "]");
            }
        }
        matcher.appendTail(sb);
        return new ChartPlaceholderResult(sb.toString(), images);
    }

    private record ChartPlaceholderResult(String content, List<File> images) {
    }

    private String buildDefaultSubject(TaskExecutionEvent event) {
        return "任务执行通知 - " + event.getTask().getTaskName();
    }

    private String formatWeComMarkdown(String content, String title) {
        StringBuilder sb = new StringBuilder();
        sb.append(title).append("\n\n").append(content);
        return sb.toString();
    }

    private String formatWeComText(String content, String title) {
        StringBuilder sb = new StringBuilder();
        sb.append(title).append("\n\n").append(content);
        return sb.toString();
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
