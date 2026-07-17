package com.mattoid.scheduled.notification.channel;

import com.mattoid.scheduled.entity.NotificationConfig;
import com.mattoid.scheduled.entity.WeComIntelligentBotConfig;
import com.mattoid.scheduled.notification.NotificationContext;
import com.mattoid.scheduled.notification.support.NotificationContentHelper;
import com.mattoid.scheduled.notification.support.NotificationFileUploader;
import com.mattoid.scheduled.service.NotificationConfigService;
import com.mattoid.scheduled.service.wecom.WeComAppManager;
import com.mattoid.scheduled.util.HtmlToMarkdownConverter;
import com.mattoid.scheduled.util.MarkdownUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.File;
import java.util.List;

/**
 * 企业微信智能机器人通知渠道（仅 CALLBACK 模式支持主动推送）。
 */
@Slf4j
@Component
public class WeComIntelligentBotNotificationChannel extends AbstractNotificationChannel {

    private final WeComAppManager weComAppManager;
    private final NotificationFileUploader fileUploader;

    public WeComIntelligentBotNotificationChannel(NotificationConfigService notificationConfigService,
                                                  WeComAppManager weComAppManager,
                                                  NotificationFileUploader fileUploader) {
        super(notificationConfigService);
        this.weComAppManager = weComAppManager;
        this.fileUploader = fileUploader;
    }

    @Override
    public String channelType() {
        return "WECOM_INTELLIGENT_BOT";
    }

    @Override
    public void send(NotificationContext context) throws Exception {
        NotificationConfig notificationConfig = requireConfig(context, "WECOM_INTELLIGENT_BOT");
        if (notificationConfig == null) {
            return;
        }
        WeComIntelligentBotConfig config = parseConfigJson(notificationConfig.getConfigJson(), WeComIntelligentBotConfig.class);
        String mode = StringUtils.hasText(config.getMode()) ? config.getMode() : "LONGCHAIN";

        if (!"CALLBACK".equals(mode)) {
            log.warn("智能机器人长链模式不支持主动推送通知，请将通知规则 {} 切换到 CALLBACK 模式", context.getRule().getId());
            return;
        }

        var rule = context.getRule();
        var event = context.getEvent();
        String toUser = StringUtils.hasText(rule.getWecomToUser()) ? rule.getWecomToUser() : "@all";
        String content = getContentOrDefault(context);
        content = MarkdownUtils.toPlainText(HtmlToMarkdownConverter.convert(content));

        var chartResult = NotificationContentHelper.replaceChartPlaceholdersForWeCom(content, event.getChartFiles());
        content = chartResult.content();

        weComAppManager.sendText(notificationConfig.getId(), toUser,
                NotificationContentHelper.formatWeComText(content, event.getTask().getTaskName()));

        for (File imageFile : chartResult.images()) {
            weComAppManager.sendImage(notificationConfig.getId(), toUser, imageFile);
        }

        List<File> reportFiles = event.getNotifyFiles();
        if (rule.getStorageConfigId() != null && reportFiles != null && !reportFiles.isEmpty()) {
            List<String> urls = fileUploader.upload(rule.getStorageConfigId(), reportFiles);
            if (!urls.isEmpty()) {
                String urlContent = "文件下载地址：\n" + String.join("\n", urls);
                weComAppManager.sendText(notificationConfig.getId(), toUser, urlContent);
            }
        } else if (reportFiles != null) {
            for (File file : reportFiles) {
                weComAppManager.sendFile(notificationConfig.getId(), toUser, file);
            }
        }
    }
}
