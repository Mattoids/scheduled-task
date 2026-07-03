package com.mattoid.scheduled.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mattoid.scheduled.common.TestConnectionResult;
import com.mattoid.scheduled.entity.EmailConfig;
import com.mattoid.scheduled.mapper.EmailConfigMapper;
import com.mattoid.scheduled.util.CryptoUtil;
import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Properties;

@Slf4j
@Service
public class EmailConfigService extends ServiceImpl<EmailConfigMapper, EmailConfig> {

    @Override
    public boolean saveOrUpdate(EmailConfig entity) {
        if (StringUtils.hasText(entity.getPassword()) && !entity.getPassword().startsWith("ENC(")) {
            entity.setPassword(CryptoUtil.encrypt(entity.getPassword()));
        }
        return super.saveOrUpdate(entity);
    }

    public TestConnectionResult testConnection(EmailConfig config) {
        if (config == null) {
            return TestConnectionResult.fail("配置不能为空");
        }
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(config.getSmtpHost());
        sender.setPort(config.getSmtpPort());
        sender.setUsername(config.getUsername());
        sender.setPassword(CryptoUtil.decryptIfNeeded(config.getPassword()));

        boolean auth = config.getAuth() != null && config.getAuth() == 1;
        boolean starttls = config.getStarttls() != null && config.getStarttls() == 1;
        boolean ssl = config.getSsl() != null && config.getSsl() == 1;

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", String.valueOf(auth));
        props.put("mail.smtp.starttls.enable", String.valueOf(starttls));
        props.put("mail.smtp.connectiontimeout", "10000");
        props.put("mail.smtp.timeout", "10000");
        if (ssl) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.ssl.checkserveridentity", "false");
            props.put("mail.smtp.ssl.trust", "*");
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            props.put("mail.smtp.socketFactory.port", String.valueOf(config.getSmtpPort()));
        }
        try {
            sender.testConnection();
            return TestConnectionResult.ok();
        } catch (MessagingException e) {
            log.error("测试邮箱配置连接失败: {}", config.getConfigName(), e);
            return TestConnectionResult.fail(e.getMessage());
        }
    }
}
