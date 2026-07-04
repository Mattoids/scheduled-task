package com.mattoid.scheduled.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mattoid.scheduled.entity.NotificationConfig;
import com.mattoid.scheduled.entity.WeComAppConfig;
import com.mattoid.scheduled.service.NotificationConfigService;
import com.mattoid.scheduled.service.wecom.WeComAppManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
public class WeComMenuRegistrar implements CommandLineRunner {

    private static final String DEFAULT_MENU = "{\"button\":[" +
            "{\"type\":\"click\",\"name\":\"查询任务\",\"key\":\"QUERY_TASKS\"}," +
            "{\"type\":\"click\",\"name\":\"运行任务\",\"key\":\"RUN_TASK_PROMPT\"}," +
            "{\"type\":\"click\",\"name\":\"帮助\",\"key\":\"HELP\"}" +
            "]}";

    private final NotificationConfigService notificationConfigService;
    private final WeComAppManager weComAppManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WeComMenuRegistrar(NotificationConfigService notificationConfigService,
                              WeComAppManager weComAppManager) {
        this.notificationConfigService = notificationConfigService;
        this.weComAppManager = weComAppManager;
    }

    @Override
    public void run(String... args) {
        notificationConfigService.lambdaQuery()
                .eq(NotificationConfig::getConfigType, "WECOM_APP")
                .eq(NotificationConfig::getStatus, 1)
                .list()
                .forEach(notificationConfig -> {
                    try {
                        WeComAppConfig config = parseConfigJson(notificationConfig.getConfigJson(), WeComAppConfig.class);
                        String menuJson = StringUtils.hasText(config.getMenuJson()) ? config.getMenuJson() : DEFAULT_MENU;
                        if (!isValidMenuJson(menuJson)) {
                            log.warn("企业微信应用菜单 JSON 格式无效，使用默认菜单: configId={}, menuJson={}",
                                    notificationConfig.getId(), menuJson);
                            menuJson = DEFAULT_MENU;
                        }
                        weComAppManager.createMenu(notificationConfig.getId(), menuJson);
                    } catch (Exception e) {
                        log.error("企业微信应用菜单注册失败: configId={}", notificationConfig.getId(), e);
                    }
                });
    }

    private boolean isValidMenuJson(String menuJson) {
        if (!StringUtils.hasText(menuJson)) {
            return false;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode node = objectMapper.readTree(menuJson);
            return node.has("button") && node.get("button").isArray();
        } catch (Exception e) {
            return false;
        }
    }

    private <T> T parseConfigJson(String configJson, Class<T> clazz) throws JsonProcessingException {
        return objectMapper.readValue(configJson, clazz);
    }
}
