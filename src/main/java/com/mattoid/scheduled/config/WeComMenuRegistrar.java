package com.mattoid.scheduled.config;

import com.mattoid.scheduled.entity.WeComAppConfig;
import com.mattoid.scheduled.service.WeComAppConfigService;
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

    private final WeComAppConfigService weComAppConfigService;
    private final WeComAppManager weComAppManager;

    public WeComMenuRegistrar(WeComAppConfigService weComAppConfigService,
                              WeComAppManager weComAppManager) {
        this.weComAppConfigService = weComAppConfigService;
        this.weComAppManager = weComAppManager;
    }

    @Override
    public void run(String... args) {
        weComAppConfigService.lambdaQuery()
                .eq(WeComAppConfig::getStatus, 1)
                .list()
                .forEach(config -> {
                    try {
                        String menuJson = StringUtils.hasText(config.getMenuJson()) ? config.getMenuJson() : DEFAULT_MENU;
                        weComAppManager.createMenu(config.getId(), menuJson);
                    } catch (Exception e) {
                        log.error("企业微信应用菜单注册失败: configId={}", config.getId(), e);
                    }
                });
    }
}
