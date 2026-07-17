package com.mattoid.scheduled.notification.channel;

import com.mattoid.scheduled.entity.NotificationConfig;
import com.mattoid.scheduled.entity.WeComBotConfig;
import com.mattoid.scheduled.notification.NotificationContext;
import com.mattoid.scheduled.notification.support.NotificationContentHelper;
import com.mattoid.scheduled.notification.support.NotificationFileUploader;
import com.mattoid.scheduled.service.NotificationConfigService;
import com.mattoid.scheduled.service.wecom.WeComBotClient;
import com.mattoid.scheduled.util.HtmlToMarkdownConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.File;
import java.util.List;

/**
 * 企业微信群机器人通知渠道。
 */
@Slf4j
@Component
public class WeComBotNotificationChannel extends AbstractNotificationChannel {

    private final WeComBotClient weComBotClient;
    private final NotificationFileUploader fileUploader;

    public WeComBotNotificationChannel(NotificationConfigService notificationConfigService,
                                       WeComBotClient weComBotClient,
                                       NotificationFileUploader fileUploader) {
        super(notificationConfigService);
        this.weComBotClient = weComBotClient;
        this.fileUploader = fileUploader;
    }

    @Override
    public String channelType() {
        return "WECOM_BOT";
    }

    @Override
    public void send(NotificationContext context) throws Exception {
        NotificationConfig notificationConfig = requireConfig(context, "WECOM_BOT");
        if (notificationConfig == null) {
            return;
        }
        WeComBotConfig config = parseConfigJson(notificationConfig.getConfigJson(), WeComBotConfig.class);

        var rule = context.getRule();
        var event = context.getEvent();
        String content = getContentOrDefault(context);
        content = HtmlToMarkdownConverter.convert(content);

        var chartResult = NotificationContentHelper.replaceChartPlaceholdersForWeCom(content, event.getChartFiles());
        content = chartResult.content();

        List<String> mentionedList = NotificationContentHelper.parseMentionedList(rule.getWecomToUser());
        weComBotClient.sendMarkdown(config.getWebhookKey(),
                NotificationContentHelper.formatWeComMarkdown(content, event.getTask().getTaskName()), mentionedList);

        for (File imageFile : chartResult.images()) {
            weComBotClient.sendImage(config.getWebhookKey(), imageFile);
        }

        List<File> reportFiles = event.getNotifyFiles();
        if (rule.getStorageConfigId() != null && reportFiles != null && !reportFiles.isEmpty()) {
            List<String> urls = fileUploader.upload(rule.getStorageConfigId(), reportFiles);
            if (!urls.isEmpty()) {
                String urlContent = "文件下载地址：\n" + String.join("\n", urls);
                weComBotClient.sendText(config.getWebhookKey(), urlContent, mentionedList);
            }
        } else if (reportFiles != null) {
            for (File file : reportFiles) {
                weComBotClient.sendFile(config.getWebhookKey(), file);
            }
        }
    }
}
