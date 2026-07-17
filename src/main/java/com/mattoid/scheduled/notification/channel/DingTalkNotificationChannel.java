package com.mattoid.scheduled.notification.channel;

import com.mattoid.scheduled.entity.DingTalkConfig;
import com.mattoid.scheduled.entity.NotificationConfig;
import com.mattoid.scheduled.notification.NotificationContext;
import com.mattoid.scheduled.notification.support.NotificationContentHelper;
import com.mattoid.scheduled.notification.support.NotificationFileUploader;
import com.mattoid.scheduled.service.NotificationConfigService;
import com.mattoid.scheduled.service.notify.DingTalkClient;
import com.mattoid.scheduled.util.CryptoUtil;
import com.mattoid.scheduled.util.HtmlToMarkdownConverter;
import com.mattoid.scheduled.util.MarkdownUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Consumer;

/**
 * 钉钉群机器人通知渠道。
 */
@Slf4j
@Component
public class DingTalkNotificationChannel extends AbstractNotificationChannel {

    private final DingTalkClient dingTalkClient;
    private final NotificationFileUploader fileUploader;

    public DingTalkNotificationChannel(NotificationConfigService notificationConfigService,
                                       DingTalkClient dingTalkClient,
                                       NotificationFileUploader fileUploader) {
        super(notificationConfigService);
        this.dingTalkClient = dingTalkClient;
        this.fileUploader = fileUploader;
    }

    @Override
    public String channelType() {
        return "DINGTALK";
    }

    @Override
    public void send(NotificationContext context) throws Exception {
        NotificationConfig notificationConfig = requireConfig(context, "DINGTALK");
        if (notificationConfig == null) {
            return;
        }
        DingTalkConfig config = parseConfigJson(notificationConfig.getConfigJson(), DingTalkConfig.class);

        var rule = context.getRule();
        var event = context.getEvent();
        String content = MarkdownUtils.toPlainText(HtmlToMarkdownConverter.convert(getContentOrDefault(context)));
        List<String> atMobiles = NotificationContentHelper.parseMentionedList(rule.getWecomToUser());

        dingTalkClient.sendMarkdown(
                config.getWebhookUrl(),
                CryptoUtil.decryptIfNeeded(config.getSecret()),
                event.getTask().getTaskName(),
                NotificationContentHelper.formatWeComMarkdown(content, event.getTask().getTaskName()),
                atMobiles,
                config.getAtAll());

        sendReportFileLinks(context, urls -> {
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
