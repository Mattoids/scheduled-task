package com.mattoid.scheduled.notification.channel;

import com.mattoid.scheduled.entity.EmailConfig;
import com.mattoid.scheduled.entity.EmailRecipient;
import com.mattoid.scheduled.entity.NotificationConfig;
import com.mattoid.scheduled.entity.NotificationRule;
import com.mattoid.scheduled.notification.NotificationContext;
import com.mattoid.scheduled.notification.support.NotificationContentHelper;
import com.mattoid.scheduled.service.EmailRecipientService;
import com.mattoid.scheduled.service.EmailSenderService;
import com.mattoid.scheduled.service.NotificationConfigService;
import com.mattoid.scheduled.util.PlaceholderUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 邮件通知渠道。
 */
@Slf4j
@Component
public class EmailNotificationChannel extends AbstractNotificationChannel {

    private final EmailRecipientService emailRecipientService;
    private final EmailSenderService emailSenderService;

    public EmailNotificationChannel(NotificationConfigService notificationConfigService,
                                    EmailRecipientService emailRecipientService,
                                    EmailSenderService emailSenderService) {
        super(notificationConfigService);
        this.emailRecipientService = emailRecipientService;
        this.emailSenderService = emailSenderService;
    }

    @Override
    public String channelType() {
        return "EMAIL";
    }

    @Override
    public String resolveRecipient(NotificationContext context) {
        var rule = context.getRule();
        return StringUtils.hasText(rule.getRecipientIds()) ? rule.getRecipientIds() : rule.getRecipientGroupIds();
    }

    @Override
    public void send(NotificationContext context) throws Exception {
        NotificationConfig notificationConfig = requireConfig(context, "EMAIL");
        if (notificationConfig == null) {
            return;
        }
        EmailConfig config = parseConfigJson(notificationConfig.getConfigJson(), EmailConfig.class);
        List<String> toList = resolveRecipients(context.getRule());
        if (toList.isEmpty()) {
            log.warn("邮件通知规则无收件人: ruleId={}", context.getRule().getId());
            return;
        }

        var event = context.getEvent();
        Map<String, Object> data = NotificationContentHelper.buildInlineResultContext(event.getInlineResults());
        String subject = resolveSubject(context, data);
        String body = resolveBody(context, data);

        Map<String, File> inlineImages = new LinkedHashMap<>();
        body = NotificationContentHelper.replaceChartPlaceholdersForEmail(body, event.getChartFiles(), inlineImages);

        emailSenderService.sendEmail(config, toList, subject, body, event.getNotifyFiles(), inlineImages);
    }

    private List<String> resolveRecipients(NotificationRule rule) {
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

    private String resolveSubject(NotificationContext context, Map<String, Object> data) {
        if (StringUtils.hasText(context.getSubject())) {
            return context.getSubject();
        }
        var rule = context.getRule();
        String subject = StringUtils.hasText(rule.getSubject())
                ? rule.getSubject()
                : NotificationContentHelper.buildDefaultSubject(context.getEvent());
        return PlaceholderUtils.replacePlaceholders(subject, data);
    }

    private String resolveBody(NotificationContext context, Map<String, Object> data) {
        if (StringUtils.hasText(context.getBody())) {
            return context.getBody();
        }
        var rule = context.getRule();
        String body = StringUtils.hasText(rule.getBody())
                ? rule.getBody()
                : NotificationContentHelper.buildDefaultSummary(context.getEvent());
        return PlaceholderUtils.replacePlaceholders(body, data);
    }
}
