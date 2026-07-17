package com.mattoid.scheduled.notification.channel;

import com.mattoid.scheduled.entity.NotificationConfig;
import com.mattoid.scheduled.entity.SlackConfig;
import com.mattoid.scheduled.notification.NotificationContext;
import com.mattoid.scheduled.notification.support.NotificationContentHelper;
import com.mattoid.scheduled.service.NotificationConfigService;
import com.mattoid.scheduled.service.notify.SlackClient;
import com.mattoid.scheduled.util.CryptoUtil;
import com.mattoid.scheduled.util.HtmlToMarkdownConverter;
import com.mattoid.scheduled.util.MarkdownUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Slack Incoming Webhook 通知渠道。
 */
@Slf4j
@Component
public class SlackNotificationChannel extends AbstractNotificationChannel {

    private final SlackClient slackClient;

    public SlackNotificationChannel(NotificationConfigService notificationConfigService,
                                    SlackClient slackClient) {
        super(notificationConfigService);
        this.slackClient = slackClient;
    }

    @Override
    public String channelType() {
        return "SLACK";
    }

    @Override
    public void send(NotificationContext context) throws Exception {
        NotificationConfig notificationConfig = requireConfig(context, "SLACK");
        if (notificationConfig == null) {
            return;
        }
        SlackConfig config = parseConfigJson(notificationConfig.getConfigJson(), SlackConfig.class);

        var event = context.getEvent();
        String content = MarkdownUtils.toPlainText(HtmlToMarkdownConverter.convert(getContentOrDefault(context)));

        slackClient.sendText(
                CryptoUtil.decryptIfNeeded(config.getWebhookUrl()),
                NotificationContentHelper.formatWeComMarkdown(content, event.getTask().getTaskName()),
                config.getChannel(),
                config.getUsername());
    }
}
