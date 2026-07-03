package com.mattoid.scheduled.service;

import com.mattoid.scheduled.entity.EmailConfig;
import com.mattoid.scheduled.util.CryptoUtil;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Properties;

@Slf4j
@Service
public class EmailSenderService {

    public void sendEmail(EmailConfig config, List<String> toList, String subject, String body, List<File> attachments) throws MessagingException, UnsupportedEncodingException {
        JavaMailSender mailSender = buildMailSender(config);
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

        mailSender.send(message);
        log.info("邮件已发送: {} 收件人: {}", subject, toList);
    }

    private JavaMailSender buildMailSender(EmailConfig config) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(config.getSmtpHost());
        sender.setPort(config.getSmtpPort());
        sender.setUsername(config.getUsername());
        sender.setPassword(CryptoUtil.decryptIfNeeded(config.getPassword()));

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", config.getAuth() != null && config.getAuth() == 1);
        props.put("mail.smtp.starttls.enable", config.getStarttls() != null && config.getStarttls() == 1);
        if (config.getSsl() != null && config.getSsl() == 1) {
            props.put("mail.smtp.ssl.enable", "true");
        }
        return sender;
    }
}
