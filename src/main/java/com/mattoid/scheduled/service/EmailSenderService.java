package com.mattoid.scheduled.service;

import com.mattoid.scheduled.entity.EmailConfig;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class EmailSenderService {

    private final NotificationConfigService notificationConfigService;

    public EmailSenderService(NotificationConfigService notificationConfigService) {
        this.notificationConfigService = notificationConfigService;
    }

    public void sendEmail(EmailConfig config, List<String> toList, String subject, String body, List<File> attachments) throws MessagingException, UnsupportedEncodingException {
        sendEmail(config, toList, subject, body, attachments, null);
    }

    public void sendEmail(EmailConfig config, List<String> toList, String subject, String body, List<File> attachments,
                          Map<String, File> inlineImages) throws MessagingException, UnsupportedEncodingException {
        JavaMailSender mailSender = notificationConfigService.buildJavaMailSender(config);
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        String from = config.getFromAddress();
        if (!StringUtils.hasText(from)) {
            throw new IllegalArgumentException("发件人地址不能为空");
        }
        String fromName = StringUtils.hasText(config.getFromName()) ? config.getFromName() : from;
        helper.setFrom(from, fromName);
        helper.setTo(toList.toArray(new String[0]));
        helper.setSubject(subject);
        helper.setText(body, true);

        if (attachments != null) {
            for (File file : attachments) {
                helper.addAttachment(file.getName(), file);
            }
        }

        if (inlineImages != null) {
            for (Map.Entry<String, File> entry : inlineImages.entrySet()) {
                helper.addInline(entry.getKey(), entry.getValue());
            }
        }

        mailSender.send(message);
        log.info("邮件已发送: {} 收件人: {}", subject, toList);
    }
}
