package com.mattoid.scheduled.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mattoid.scheduled.common.TestConnectionResult;
import com.mattoid.scheduled.entity.EmailConfig;
import com.mattoid.scheduled.entity.NotificationConfig;
import com.mattoid.scheduled.entity.WeComAppConfig;
import com.mattoid.scheduled.entity.WeComBotConfig;
import com.mattoid.scheduled.mapper.NotificationConfigMapper;
import com.mattoid.scheduled.service.wecom.WeComAppManager;
import com.mattoid.scheduled.service.wecom.WeComBotClient;
import com.mattoid.scheduled.util.CryptoUtil;
import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Properties;

@Slf4j
@Service
public class NotificationConfigService extends ServiceImpl<NotificationConfigMapper, NotificationConfig> {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WeComAppManager weComAppManager;
    private final WeComBotClient weComBotClient;

    public NotificationConfigService(WeComAppManager weComAppManager,
                                     WeComBotClient weComBotClient) {
        this.weComAppManager = weComAppManager;
        this.weComBotClient = weComBotClient;
    }

    @Override
    public boolean saveOrUpdate(NotificationConfig entity) {
        entity.setConfigJson(encryptSecrets(entity.getConfigType(), entity.getConfigJson()));
        return super.saveOrUpdate(entity);
    }

    private String encryptSecrets(String configType, String configJson) {
        if (!StringUtils.hasText(configJson)) {
            return configJson;
        }
        try {
            Map<String, Object> map = objectMapper.readValue(configJson, Map.class);
            if ("EMAIL".equals(configType)) {
                encryptField(map, "password");
            } else if ("WECOM_APP".equals(configType) || "WECOM_INTELLIGENT_BOT".equals(configType)) {
                encryptField(map, "secret");
            }
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("配置 JSON 格式错误", e);
        }
    }

    private void encryptField(Map<String, Object> map, String field) {
        Object value = map.get(field);
        if (value instanceof String s && StringUtils.hasText(s) && !s.startsWith("ENC(")) {
            map.put(field, CryptoUtil.encrypt(s));
        }
    }

    public TestConnectionResult testConnection(NotificationConfig config) {
        if (config == null) {
            return TestConnectionResult.fail("配置不能为空");
        }
        String type = config.getConfigType();
        if (!StringUtils.hasText(type)) {
            return TestConnectionResult.fail("配置类型不能为空");
        }
        try {
            return switch (type) {
                case "EMAIL" -> testEmail(config);
                case "WECOM_APP" -> testWeComApp(config);
                case "WECOM_BOT" -> testWeComBot(config);
                case "WECOM_INTELLIGENT_BOT" -> testWeComIntelligentBot(config);
                default -> TestConnectionResult.fail("未知的配置类型: " + type);
            };
        } catch (Exception e) {
            log.error("测试通知配置失败: {}", config.getConfigName(), e);
            return TestConnectionResult.fail(e.getMessage());
        }
    }

    private TestConnectionResult testEmail(NotificationConfig config) throws JsonProcessingException, MessagingException {
        EmailConfig emailConfig = parseConfigJson(config.getConfigJson(), EmailConfig.class);
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(emailConfig.getSmtpHost());
        sender.setPort(emailConfig.getSmtpPort());
        sender.setUsername(emailConfig.getUsername());
        sender.setPassword(CryptoUtil.decryptIfNeeded(emailConfig.getPassword()));

        boolean auth = emailConfig.getAuth() != null && emailConfig.getAuth() == 1;
        boolean starttls = emailConfig.getStarttls() != null && emailConfig.getStarttls() == 1;
        boolean ssl = emailConfig.getSsl() != null && emailConfig.getSsl() == 1;

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
            props.put("mail.smtp.socketFactory.port", String.valueOf(emailConfig.getSmtpPort()));
        }
        sender.testConnection();
        return TestConnectionResult.ok();
    }

    private TestConnectionResult testWeComApp(NotificationConfig config) throws Exception {
        WeComAppConfig appConfig = parseConfigJson(config.getConfigJson(), WeComAppConfig.class);
        WeComAppConfig temp = new WeComAppConfig();
        temp.setCorpId(appConfig.getCorpId());
        temp.setAgentId(appConfig.getAgentId());
        temp.setSecret(CryptoUtil.decryptIfNeeded(appConfig.getSecret()));
        temp.setToken(appConfig.getToken());
        temp.setAesKey(appConfig.getAesKey());
        temp.setProxyUrl(appConfig.getProxyUrl());
        temp.setStatus(1);
        weComAppManager.buildTempService(temp).getAccessToken();
        return TestConnectionResult.ok();
    }

    private TestConnectionResult testWeComBot(NotificationConfig config) throws Exception {
        WeComBotConfig botConfig = parseConfigJson(config.getConfigJson(), WeComBotConfig.class);
        weComBotClient.sendText(botConfig.getWebhookKey(), "连接测试", null);
        return TestConnectionResult.ok();
    }

    private TestConnectionResult testWeComIntelligentBot(NotificationConfig config) throws Exception {
        com.mattoid.scheduled.entity.WeComIntelligentBotConfig botConfig = parseConfigJson(config.getConfigJson(), com.mattoid.scheduled.entity.WeComIntelligentBotConfig.class);
        // 验证鉴权：获取 access token
        weComAppManager.buildTempService(botConfig).getAccessToken();
        return TestConnectionResult.ok();
    }

    public <T> T parseConfigJson(String configJson, Class<T> clazz) throws JsonProcessingException {
        return objectMapper.readValue(configJson, clazz);
    }
}
