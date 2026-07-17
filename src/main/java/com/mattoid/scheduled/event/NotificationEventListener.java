package com.mattoid.scheduled.event;

import com.mattoid.scheduled.entity.NotificationConfig;
import com.mattoid.scheduled.entity.NotificationLog;
import com.mattoid.scheduled.entity.NotificationRule;
import com.mattoid.scheduled.notification.NotificationChannel;
import com.mattoid.scheduled.notification.NotificationContext;
import com.mattoid.scheduled.notification.channel.AiOptimizingNotificationChannel;
import com.mattoid.scheduled.notification.support.NotificationContentHelper;
import com.mattoid.scheduled.service.AiAssistantService;
import com.mattoid.scheduled.service.NotificationConfigService;
import com.mattoid.scheduled.service.NotificationLogService;
import com.mattoid.scheduled.service.NotificationRuleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 任务执行事件监听器，仅负责根据规则路由到对应通知渠道并进行重试。
 *
 * 具体渠道发送逻辑已拆分到 {@link NotificationChannel} SPI 的各个实现中。
 */
@Slf4j
@Component
public class NotificationEventListener {

    private static final int MAX_RETRY = 2;
    private static final long RETRY_DELAY_MS = 1000;

    private final NotificationRuleService notificationRuleService;
    private final NotificationConfigService notificationConfigService;
    private final NotificationLogService notificationLogService;
    private final AiAssistantService aiAssistantService;
    private final Map<String, NotificationChannel> channelMap;

    public NotificationEventListener(NotificationRuleService notificationRuleService,
                                     NotificationConfigService notificationConfigService,
                                     NotificationLogService notificationLogService,
                                     AiAssistantService aiAssistantService,
                                     List<NotificationChannel> channels) {
        this.notificationRuleService = notificationRuleService;
        this.notificationConfigService = notificationConfigService;
        this.notificationLogService = notificationLogService;
        this.aiAssistantService = aiAssistantService;
        this.channelMap = channels.stream()
                .collect(Collectors.toMap(NotificationChannel::channelType, Function.identity(),
                        (existing, replacement) -> existing));
    }

    @Async
    @EventListener
    public void onTaskExecutionEvent(TaskExecutionEvent event) {
        String taskCode = event.getTask() != null ? event.getTask().getTaskCode() : null;
        List<NotificationRule> rules = notificationRuleService.findEnabledByEventTypeAndTask(event.getEventType().name(), taskCode);
        if (rules.isEmpty()) {
            return;
        }
        for (NotificationRule rule : rules) {
            dispatchWithRetry(event, rule);
        }
    }

    private void dispatchWithRetry(TaskExecutionEvent event, NotificationRule rule) {
        NotificationConfig config = resolveNotificationConfig(rule);
        NotificationContext context = new NotificationContext(event, rule, config);
        String content = NotificationContentHelper.buildDefaultSummary(event);
        NotificationChannel channel = channelMap.get(rule.getChannel());
        String recipient = channel != null ? channel.resolveRecipient(context) : null;

        for (int attempt = 0; attempt <= MAX_RETRY; attempt++) {
            NotificationLog notificationLog = createLog(event, rule, content, recipient, attempt);
            try {
                sendChannel(context, channel);
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

    private void sendChannel(NotificationContext context, NotificationChannel channel) throws Exception {
        if (channel == null) {
            log.warn("未知的通知渠道: {}", context.getRule().getChannel());
            return;
        }
        NotificationChannel target = channel;
        if (context.getRule().getAiOptimizeNotify() != null && context.getRule().getAiOptimizeNotify() == 1) {
            target = new AiOptimizingNotificationChannel(channel, aiAssistantService);
        }
        target.send(context);
    }

    private NotificationConfig resolveNotificationConfig(NotificationRule rule) {
        if (rule == null || (rule.getConfigId() == null && !StringUtils.hasText(rule.getConfigCode()))) {
            return null;
        }
        if (rule.getConfigId() != null) {
            NotificationConfig config = notificationConfigService.getById(rule.getConfigId());
            if (config != null) {
                return config;
            }
        }
        if (StringUtils.hasText(rule.getConfigCode())) {
            return notificationConfigService.getByCode(rule.getConfigCode());
        }
        return null;
    }

    private NotificationLog createLog(TaskExecutionEvent event, NotificationRule rule, String content,
                                      String recipient, int attempt) {
        NotificationLog log = new NotificationLog();
        log.setTaskId(event.getTask() != null ? event.getTask().getId() : null);
        log.setEventType(event.getEventType() != null ? event.getEventType().name() : null);
        log.setRuleId(rule.getId());
        log.setChannel(rule.getChannel());
        log.setConfigId(rule.getConfigId());
        log.setRecipient(recipient);
        log.setContent(truncate(content, 2000));
        log.setRetryCount(attempt);
        return log;
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }
}
