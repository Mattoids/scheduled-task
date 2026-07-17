package com.mattoid.scheduled.notification.channel;

import com.mattoid.scheduled.entity.NotificationConfig;
import com.mattoid.scheduled.entity.WebhookConfig;
import com.mattoid.scheduled.notification.NotificationContext;
import com.mattoid.scheduled.notification.support.NotificationContentHelper;
import com.mattoid.scheduled.service.NotificationConfigService;
import com.mattoid.scheduled.service.notify.WebhookClient;
import com.mattoid.scheduled.util.CryptoUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 自定义 Webhook 通知渠道。
 */
@Slf4j
@Component
public class WebhookNotificationChannel extends AbstractNotificationChannel {

    private final WebhookClient webhookClient;

    public WebhookNotificationChannel(NotificationConfigService notificationConfigService,
                                      WebhookClient webhookClient) {
        super(notificationConfigService);
        this.webhookClient = webhookClient;
    }

    @Override
    public String channelType() {
        return "WEBHOOK";
    }

    @Override
    public void send(NotificationContext context) throws Exception {
        NotificationConfig notificationConfig = requireConfig(context, "WEBHOOK");
        if (notificationConfig == null) {
            return;
        }
        WebhookConfig config = parseConfigJson(notificationConfig.getConfigJson(), WebhookConfig.class);

        var event = context.getEvent();
        String content = getContentOrDefault(context);
        Map<String, Object> placeholders = new HashMap<>(
                webhookClient.buildPlaceholders(event.getTask().getTaskName(), content));
        placeholders.putAll(NotificationContentHelper.buildInlineResultContext(event.getInlineResults()));

        Map<String, String> headers = decryptWebhookHeaders(config.getHeaders());
        webhookClient.send(
                CryptoUtil.decryptIfNeeded(config.getUrl()),
                config.getMethod(),
                headers,
                config.getBodyTemplate(),
                placeholders,
                config.getTimeoutSeconds());
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
}
