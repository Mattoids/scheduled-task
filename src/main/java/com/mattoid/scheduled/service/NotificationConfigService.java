package com.mattoid.scheduled.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mattoid.scheduled.common.TestConnectionResult;
import com.mattoid.scheduled.entity.*;
import com.mattoid.scheduled.mapper.NotificationConfigMapper;
import com.mattoid.scheduled.service.notify.DingTalkClient;
import com.mattoid.scheduled.service.notify.FeishuClient;
import com.mattoid.scheduled.service.notify.SlackClient;
import com.mattoid.scheduled.service.notify.WebhookClient;
import com.mattoid.scheduled.service.wecom.WeComAppManager;
import com.mattoid.scheduled.service.wecom.WeComBotClient;
import com.mattoid.scheduled.service.wecom.WeComIntelligentBotClient;
import com.mattoid.scheduled.util.CryptoUtil;
import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@Slf4j
@Service
public class NotificationConfigService extends ServiceImpl<NotificationConfigMapper, NotificationConfig> {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WeComAppManager weComAppManager;
    private final WeComBotClient weComBotClient;
    private final WeComIntelligentBotClient weComIntelligentBotClient;
    private final DingTalkClient dingTalkClient;
    private final FeishuClient feishuClient;
    private final SlackClient slackClient;
    private final WebhookClient webhookClient;

    public NotificationConfigService(WeComAppManager weComAppManager,
                                     WeComBotClient weComBotClient,
                                     WeComIntelligentBotClient weComIntelligentBotClient,
                                     DingTalkClient dingTalkClient,
                                     FeishuClient feishuClient,
                                     SlackClient slackClient,
                                     WebhookClient webhookClient) {
        this.weComAppManager = weComAppManager;
        this.weComBotClient = weComBotClient;
        this.weComIntelligentBotClient = weComIntelligentBotClient;
        this.dingTalkClient = dingTalkClient;
        this.feishuClient = feishuClient;
        this.slackClient = slackClient;
        this.webhookClient = webhookClient;
    }

    @Override
    public boolean saveOrUpdate(NotificationConfig entity) {
        validateConfigCode(entity);
        entity.setConfigJson(encryptSecrets(entity.getConfigType(), entity.getConfigJson()));
        return super.saveOrUpdate(entity);
    }

    private void validateConfigCode(NotificationConfig entity) {
        String configCode = entity.getConfigCode();
        if (!StringUtils.hasText(configCode)) {
            throw new IllegalArgumentException("配置编码不能为空");
        }
        configCode = configCode.trim();
        entity.setConfigCode(configCode);
        NotificationConfig existing = lambdaQuery()
                .eq(NotificationConfig::getConfigCode, configCode)
                .ne(entity.getId() != null, NotificationConfig::getId, entity.getId())
                .one();
        if (existing != null) {
            throw new IllegalArgumentException("配置编码已存在: " + configCode);
        }
    }

    public String decryptSecrets(String configType, String configJson) {
        if (!StringUtils.hasText(configJson)) {
            return configJson;
        }
        try {
            Map<String, Object> map = objectMapper.readValue(configJson, Map.class);
            switch (configType) {
                case "EMAIL" -> decryptField(map, "password");
                case "WECOM_APP" -> decryptField(map, "secret");
                case "WECOM_INTELLIGENT_BOT" -> {
                    String mode = (String) map.get("mode");
                    if ("CALLBACK".equals(mode)) {
                        decryptField(map, "secret");
                    } else {
                        decryptField(map, "botSecret");
                    }
                }
                case "DINGTALK", "FEISHU" -> decryptField(map, "secret");
                case "SLACK" -> decryptField(map, "webhookUrl");
                case "WEBHOOK" -> {
                    decryptField(map, "url");
                    Map<String, Object> headers = (Map<String, Object>) map.get("headers");
                    if (headers != null) {
                        for (String key : headers.keySet()) {
                            if (isSensitiveHeader(key)) {
                                Object value = headers.get(key);
                                if (value instanceof String s) {
                                    headers.put(key, CryptoUtil.decryptIfNeeded(s));
                                }
                            }
                        }
                    }
                }
            }
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.error("解密配置 JSON 失败", e);
            return configJson;
        }
    }

    private void decryptField(Map<String, Object> map, String field) {
        Object value = map.get(field);
        if (value instanceof String s) {
            map.put(field, CryptoUtil.decryptIfNeeded(s));
        }
    }

    private String encryptSecrets(String configType, String configJson) {
        if (!StringUtils.hasText(configJson)) {
            return configJson;
        }
        try {
            Map<String, Object> map = objectMapper.readValue(configJson, Map.class);
            switch (configType) {
                case "EMAIL" -> encryptField(map, "password");
                case "WECOM_APP" -> encryptField(map, "secret");
                case "WECOM_INTELLIGENT_BOT" -> {
                    String mode = (String) map.get("mode");
                    if ("CALLBACK".equals(mode)) {
                        encryptField(map, "secret");
                    } else {
                        encryptField(map, "botSecret");
                    }
                }
                case "DINGTALK", "FEISHU" -> encryptField(map, "secret");
                case "SLACK" -> encryptField(map, "webhookUrl");
                case "WEBHOOK" -> {
                    encryptField(map, "url");
                    Map<String, Object> headers = (Map<String, Object>) map.get("headers");
                    if (headers != null) {
                        for (String key : headers.keySet()) {
                            if (isSensitiveHeader(key)) {
                                Object value = headers.get(key);
                                if (value instanceof String s && StringUtils.hasText(s) && !s.startsWith("ENC(")) {
                                    headers.put(key, CryptoUtil.encrypt(s));
                                }
                            }
                        }
                    }
                }
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

    private boolean isSensitiveHeader(String key) {
        if (!StringUtils.hasText(key)) {
            return false;
        }
        String lower = key.toLowerCase();
        return lower.contains("authorization") || lower.contains("token") || lower.contains("api-key") || lower.contains("apikey") || lower.contains("secret");
    }

    public NotificationConfig getByCode(String configCode) {
        if (!StringUtils.hasText(configCode)) {
            return null;
        }
        return lambdaQuery().eq(NotificationConfig::getConfigCode, configCode).one();
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
                case "DINGTALK" -> testDingTalk(config);
                case "FEISHU" -> testFeishu(config);
                case "SLACK" -> testSlack(config);
                case "WEBHOOK" -> testWebhook(config);
                default -> TestConnectionResult.fail("未知的配置类型: " + type);
            };
        } catch (Exception e) {
            log.error("测试通知配置失败: {}", config.getConfigName(), e);
            return TestConnectionResult.fail(e.getMessage());
        }
    }

    private TestConnectionResult testEmail(NotificationConfig config) throws JsonProcessingException, MessagingException {
        EmailConfig emailConfig = parseConfigJson(config.getConfigJson(), EmailConfig.class);
        JavaMailSenderImpl sender = buildJavaMailSender(emailConfig);
        sender.testConnection();
        return TestConnectionResult.ok();
    }

    JavaMailSenderImpl buildJavaMailSender(EmailConfig emailConfig) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(emailConfig.getSmtpHost());
        sender.setPort(emailConfig.getSmtpPort());
        sender.setUsername(emailConfig.getUsername());
        sender.setPassword(CryptoUtil.decryptIfNeeded(emailConfig.getPassword()));

        boolean auth = emailConfig.getAuth() != null && emailConfig.getAuth() == 1;
        int port = emailConfig.getSmtpPort() != null ? emailConfig.getSmtpPort() : 25;

        // 根据标准端口自动选择协议，避免 SSL/STARTTLS 同时开启导致连接异常
        boolean useSsl = false;
        boolean useStarttls = false;
        if (port == 465) {
            useSsl = true;
        } else if (port == 587) {
            useStarttls = true;
        } else {
            useSsl = emailConfig.getSsl() != null && emailConfig.getSsl() == 1;
            useStarttls = emailConfig.getStarttls() != null && emailConfig.getStarttls() == 1;
        }

        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", String.valueOf(auth));
        props.put("mail.smtp.starttls.enable", String.valueOf(useStarttls));
        if (useStarttls) {
            props.put("mail.smtp.starttls.required", "true");
        }
        props.put("mail.smtp.connectiontimeout", "30000");
        props.put("mail.smtp.timeout", "30000");
        if (useSsl || useStarttls) {
            // 腾讯企业邮箱等部分服务器对 TLS 版本敏感，强制使用 TLSv1.2
            props.put("mail.smtp.ssl.protocols", "TLSv1.2");
        }
        if (useSsl) {
            props.put("mail.smtp.ssl.enable", "true");
            props.put("mail.smtp.ssl.required", "true");
            props.put("mail.smtp.ssl.checkserveridentity", "false");
            props.put("mail.smtp.ssl.trust", "*");
            props.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
            props.put("mail.smtp.socketFactory.fallback", "false");
            props.put("mail.smtp.socketFactory.port", String.valueOf(port));
        }
        return sender;
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
        String mode = StringUtils.hasText(botConfig.getMode()) ? botConfig.getMode() : "LONGCHAIN";

        if ("CALLBACK".equals(mode)) {
            // 回调模式：通过 WeComAppManager 测试（同 WECOM_APP）
            WeComAppConfig temp = new WeComAppConfig();
            temp.setCorpId(botConfig.getCorpId());
            temp.setAgentId(botConfig.getAgentId());
            temp.setSecret(CryptoUtil.decryptIfNeeded(botConfig.getSecret()));
            temp.setToken(botConfig.getToken());
            temp.setAesKey(botConfig.getAesKey());
            weComAppManager.buildTempService(temp).getAccessToken();
        } else {
            // 长链模式：通过 WebSocket + aibot_subscribe 测试订阅是否成功
            if (StringUtils.hasText(botConfig.getBotSecret())) {
                botConfig.setBotSecret(CryptoUtil.decryptIfNeeded(botConfig.getBotSecret()));
            }
            weComIntelligentBotClient.testConnection(botConfig);
        }
        return TestConnectionResult.ok();
    }

    public <T> T parseConfigJson(String configJson, Class<T> clazz) throws JsonProcessingException {
        return objectMapper.readValue(configJson, clazz);
    }

    private TestConnectionResult testDingTalk(NotificationConfig config) throws Exception {
        DingTalkConfig dingTalkConfig = parseConfigJson(config.getConfigJson(), DingTalkConfig.class);
        dingTalkClient.sendText(
                dingTalkConfig.getWebhookUrl(),
                CryptoUtil.decryptIfNeeded(dingTalkConfig.getSecret()),
                "连接测试",
                null,
                false);
        return TestConnectionResult.ok();
    }

    private TestConnectionResult testFeishu(NotificationConfig config) throws Exception {
        FeishuConfig feishuConfig = parseConfigJson(config.getConfigJson(), FeishuConfig.class);
        feishuClient.sendText(
                feishuConfig.getWebhookUrl(),
                CryptoUtil.decryptIfNeeded(feishuConfig.getSecret()),
                "连接测试");
        return TestConnectionResult.ok();
    }

    private TestConnectionResult testSlack(NotificationConfig config) throws Exception {
        SlackConfig slackConfig = parseConfigJson(config.getConfigJson(), SlackConfig.class);
        slackClient.sendText(
                CryptoUtil.decryptIfNeeded(slackConfig.getWebhookUrl()),
                "连接测试",
                slackConfig.getChannel(),
                slackConfig.getUsername());
        return TestConnectionResult.ok();
    }

    private TestConnectionResult testWebhook(NotificationConfig config) throws Exception {
        WebhookConfig webhookConfig = parseConfigJson(config.getConfigJson(), WebhookConfig.class);
        Map<String, String> headers = decryptWebhookHeaders(webhookConfig.getHeaders());
        webhookClient.send(
                CryptoUtil.decryptIfNeeded(webhookConfig.getUrl()),
                webhookConfig.getMethod(),
                headers,
                webhookConfig.getBodyTemplate(),
                webhookClient.buildPlaceholders("连接测试", "连接测试"),
                webhookConfig.getTimeoutSeconds());
        return TestConnectionResult.ok();
    }

    private Map<String, String> decryptWebhookHeaders(Map<String, String> headers) {
        if (headers == null) {
            return null;
        }
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            result.put(entry.getKey(), CryptoUtil.decryptIfNeeded(entry.getValue()));
        }
        return result;
    }
}
