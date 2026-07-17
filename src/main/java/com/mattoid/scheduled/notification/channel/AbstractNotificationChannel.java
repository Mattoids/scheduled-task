package com.mattoid.scheduled.notification.channel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.mattoid.scheduled.entity.NotificationConfig;
import com.mattoid.scheduled.notification.NotificationChannel;
import com.mattoid.scheduled.notification.NotificationContext;
import com.mattoid.scheduled.service.NotificationConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

/**
 * 通知渠道抽象基类，封装配置类型校验与 JSON 反序列化。
 */
@Slf4j
public abstract class AbstractNotificationChannel implements NotificationChannel {

    protected final NotificationConfigService notificationConfigService;

    protected AbstractNotificationChannel(NotificationConfigService notificationConfigService) {
        this.notificationConfigService = notificationConfigService;
    }

    protected NotificationConfig requireConfig(NotificationContext context, String expectedType) {
        NotificationConfig config = context.getConfig();
        if (config == null) {
            log.warn("{} 配置不存在: ruleId={}", expectedType, context.getRule().getId());
            return null;
        }
        if (!expectedType.equals(config.getConfigType()) || config.getStatus() == null || config.getStatus() != 1) {
            log.warn("{} 配置不可用: ruleId={}, configId={}", expectedType, context.getRule().getId(), config.getId());
            return null;
        }
        return config;
    }

    protected <T> T parseConfigJson(String configJson, Class<T> clazz) throws JsonProcessingException {
        return notificationConfigService.parseConfigJson(configJson, clazz);
    }

    protected String getContentOrDefault(NotificationContext context) {
        if (StringUtils.hasText(context.getBody())) {
            return context.getBody();
        }
        var rule = context.getRule();
        var event = context.getEvent();
        var data = com.mattoid.scheduled.notification.support.NotificationContentHelper.buildInlineResultContext(event.getInlineResults());
        if (StringUtils.hasText(rule.getContent())) {
            return com.mattoid.scheduled.util.PlaceholderUtils.replacePlaceholders(rule.getContent(), data);
        }
        if (StringUtils.hasText(rule.getBody())) {
            return com.mattoid.scheduled.util.PlaceholderUtils.replacePlaceholders(rule.getBody(), data);
        }
        return com.mattoid.scheduled.notification.support.NotificationContentHelper.buildDefaultSummary(event);
    }
}
