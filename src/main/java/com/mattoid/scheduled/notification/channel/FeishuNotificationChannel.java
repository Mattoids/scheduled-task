package com.mattoid.scheduled.notification.channel;

import com.mattoid.scheduled.entity.FeishuConfig;
import com.mattoid.scheduled.entity.NotificationConfig;
import com.mattoid.scheduled.notification.NotificationContext;
import com.mattoid.scheduled.notification.support.NotificationContentHelper;
import com.mattoid.scheduled.notification.support.NotificationFileUploader;
import com.mattoid.scheduled.service.NotificationConfigService;
import com.mattoid.scheduled.service.notify.FeishuClient;
import com.mattoid.scheduled.util.CryptoUtil;
import com.mattoid.scheduled.util.HtmlToMarkdownConverter;
import com.mattoid.scheduled.util.MarkdownUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * 飞书群机器人通知渠道。
 */
@Slf4j
@Component
public class FeishuNotificationChannel extends AbstractNotificationChannel {

    private final FeishuClient feishuClient;
    private final NotificationFileUploader fileUploader;

    public FeishuNotificationChannel(NotificationConfigService notificationConfigService,
                                     FeishuClient feishuClient,
                                     NotificationFileUploader fileUploader) {
        super(notificationConfigService);
        this.feishuClient = feishuClient;
        this.fileUploader = fileUploader;
    }

    @Override
    public String channelType() {
        return "FEISHU";
    }

    @Override
    public void send(NotificationContext context) throws Exception {
        NotificationConfig notificationConfig = requireConfig(context, "FEISHU");
        if (notificationConfig == null) {
            return;
        }
        FeishuConfig config = parseConfigJson(notificationConfig.getConfigJson(), FeishuConfig.class);

        var event = context.getEvent();
        String content = MarkdownUtils.toPlainText(HtmlToMarkdownConverter.convert(getContentOrDefault(context)));

        feishuClient.sendText(
                config.getWebhookUrl(),
                CryptoUtil.decryptIfNeeded(config.getSecret()),
                NotificationContentHelper.formatWeComMarkdown(content, event.getTask().getTaskName()));

        sendReportFileLinks(context, urls -> {
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

    private void sendReportFileLinks(NotificationContext context, Consumer<List<String>> sender) {
        var rule = context.getRule();
        var reportFiles = context.getEvent().getNotifyFiles();
        if (rule.getStorageConfigId() != null && reportFiles != null && !reportFiles.isEmpty()) {
            List<String> urls = fileUploader.upload(rule.getStorageConfigId(), reportFiles);
            if (!urls.isEmpty()) {
                sender.accept(urls);
            }
        }
    }
}
