package com.mattoid.scheduled.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mattoid.scheduled.entity.NotificationConfig;
import com.mattoid.scheduled.service.NotificationConfigService;
import com.mattoid.scheduled.service.wecom.WeComIntelligentBotClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 应用启动后自动连接所有「已启用 + 长链模式」的企业微信智能机器人。
 * 否则长链机器人虽有配置却从未建立 WebSocket，无法接收用户消息（之前消息误打到 HTTP 回调端点并报错）。
 */
@Slf4j
@Component
public class WeComIntelligentBotAutoConnector implements CommandLineRunner {

    private final NotificationConfigService notificationConfigService;
    private final WeComIntelligentBotClient weComIntelligentBotClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WeComIntelligentBotAutoConnector(NotificationConfigService notificationConfigService,
                                            WeComIntelligentBotClient weComIntelligentBotClient) {
        this.notificationConfigService = notificationConfigService;
        this.weComIntelligentBotClient = weComIntelligentBotClient;
    }

    @Override
    public void run(String... args) {
        List<NotificationConfig> configs = notificationConfigService.lambdaQuery()
                .eq(NotificationConfig::getConfigType, "WECOM_INTELLIGENT_BOT")
                .eq(NotificationConfig::getStatus, 1)
                .list();
        for (NotificationConfig nc : configs) {
            if (!isLongChain(nc.getConfigJson())) {
                continue;
            }
            try {
                log.info("启动自动连接长链智能机器人: configId={}, name={}", nc.getId(), nc.getConfigName());
                weComIntelligentBotClient.connect(nc.getId());
            } catch (Exception e) {
                log.error("自动连接长链智能机器人失败: configId={}", nc.getId(), e);
            }
        }
    }

    private boolean isLongChain(String configJson) {
        if (configJson == null || configJson.isBlank()) {
            return false;
        }
        try {
            Map<?, ?> map = objectMapper.readValue(configJson, Map.class);
            return "LONGCHAIN".equalsIgnoreCase(String.valueOf(map.get("mode")));
        } catch (Exception e) {
            return false;
        }
    }
}
