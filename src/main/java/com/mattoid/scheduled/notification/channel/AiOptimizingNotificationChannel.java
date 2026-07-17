package com.mattoid.scheduled.notification.channel;

import com.mattoid.scheduled.notification.NotificationChannel;
import com.mattoid.scheduled.notification.NotificationContext;
import com.mattoid.scheduled.notification.support.NotificationContentHelper;
import com.mattoid.scheduled.service.AiAssistantService;
import com.mattoid.scheduled.util.PlaceholderUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * AI 优化通知内容装饰器。
 *
 * 在真正发送前，先根据规则模板或默认摘要生成内容，调用 AI 服务优化，
 * 并将优化后的主题/正文写入上下文，供被装饰的渠道使用。
 */
@Slf4j
public class AiOptimizingNotificationChannel implements NotificationChannel {

    private final NotificationChannel delegate;
    private final AiAssistantService aiAssistantService;

    public AiOptimizingNotificationChannel(NotificationChannel delegate, AiAssistantService aiAssistantService) {
        this.delegate = delegate;
        this.aiAssistantService = aiAssistantService;
    }

    @Override
    public String channelType() {
        return delegate.channelType();
    }

    @Override
    public String resolveRecipient(NotificationContext context) {
        return delegate.resolveRecipient(context);
    }

    @Override
    public void send(NotificationContext context) throws Exception {
        var rule = context.getRule();
        var event = context.getEvent();

        String subject = StringUtils.hasText(rule.getSubject())
                ? rule.getSubject()
                : NotificationContentHelper.buildDefaultSubject(event);
        String body = StringUtils.hasText(rule.getBody())
                ? rule.getBody()
                : (StringUtils.hasText(rule.getContent()) ? rule.getContent() : NotificationContentHelper.buildDefaultSummary(event));

        Map<String, Object> data = NotificationContentHelper.buildInlineResultContext(event.getInlineResults());
        subject = PlaceholderUtils.replacePlaceholders(subject, data);
        body = PlaceholderUtils.replacePlaceholders(body, data);

        String aiContext = NotificationContentHelper.buildAiNotificationContext(event.getTask(), event);
        AiAssistantService.NotificationContent optimized = aiAssistantService.optimizeNotification(
                subject, body, aiContext, rule.getAiConfigId());

        context.setSubject(optimized.subject());
        context.setBody(optimized.body());
        log.info("通知规则 {} 已使用 AI 优化通知内容", rule.getId());

        delegate.send(context);
    }
}
