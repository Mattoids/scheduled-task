package com.mattoid.scheduled.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mattoid.scheduled.entity.NotificationConfig;
import com.mattoid.scheduled.entity.TaskConfig;
import com.mattoid.scheduled.entity.TaskLog;
import com.mattoid.scheduled.entity.WeComAppConfig;
import com.mattoid.scheduled.mapper.TaskLogMapper;
import com.mattoid.scheduled.service.NotificationConfigService;
import com.mattoid.scheduled.service.TaskConfigService;
import com.mattoid.scheduled.service.wecom.WeComAppManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class WeComMenuRegistrar implements CommandLineRunner {

    private static final int TOP_TASKS_COUNT = 5;

    private final NotificationConfigService notificationConfigService;
    private final TaskConfigService taskConfigService;
    private final TaskLogMapper taskLogMapper;
    private final WeComAppManager weComAppManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WeComMenuRegistrar(NotificationConfigService notificationConfigService,
                              TaskConfigService taskConfigService,
                              TaskLogMapper taskLogMapper,
                              WeComAppManager weComAppManager) {
        this.notificationConfigService = notificationConfigService;
        this.taskConfigService = taskConfigService;
        this.taskLogMapper = taskLogMapper;
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
                        String menuJson = StringUtils.hasText(config.getMenuJson()) ? config.getMenuJson() : null;
                        if (!StringUtils.hasText(menuJson) || !isValidMenuJson(menuJson)) {
                            if (StringUtils.hasText(menuJson)) {
                                log.warn("企业微信应用菜单 JSON 格式无效，使用默认动态菜单: configId={}, menuJson={}",
                                        notificationConfig.getId(), menuJson);
                            }
                            menuJson = buildDefaultMenu();
                        }
                        weComAppManager.createMenu(notificationConfig.getId(), menuJson);
                    } catch (Exception e) {
                        log.error("企业微信应用菜单注册失败: configId={}", notificationConfig.getId(), e);
                    }
                });
    }

    private String buildDefaultMenu() throws JsonProcessingException {
        List<TaskConfig> tasks = taskConfigService.list(
                new LambdaQueryWrapper<TaskConfig>()
                        .eq(TaskConfig::getStatus, "ENABLE")
                        .eq(TaskConfig::getInWecomMenu, 1)
                        .orderByDesc(TaskConfig::getSortOrder)
                        .orderByDesc(TaskConfig::getCreateTime)
                        .last("LIMIT " + TOP_TASKS_COUNT)
        );
        List<Map<String, Object>> quickSubButtons = new ArrayList<>();
        for (TaskConfig task : tasks) {
            Map<String, Object> btn = new LinkedHashMap<>();
            btn.put("type", "click");
            btn.put("name", task.getTaskName());
            btn.put("key", "RUN_TASK_" + task.getId());
            quickSubButtons.add(btn);
        }

        List<Map<String, Object>> allButtons = new ArrayList<>();

        // Task quick-run sub-menu (top 3 recent tasks)
        if (!quickSubButtons.isEmpty()) {
            Map<String, Object> taskSubMenu = new LinkedHashMap<>();
            taskSubMenu.put("name", "快速运行");
            taskSubMenu.put("sub_button", quickSubButtons);
            allButtons.add(taskSubMenu);
        }

        // Query tasks
        Map<String, Object> queryBtn = new LinkedHashMap<>();
        queryBtn.put("type", "click");
        queryBtn.put("name", "查询任务");
        queryBtn.put("key", "QUERY_TASKS");
        allButtons.add(queryBtn);

        // Help
        Map<String, Object> helpBtn = new LinkedHashMap<>();
        helpBtn.put("type", "click");
        helpBtn.put("name", "帮助");
        helpBtn.put("key", "HELP");
        allButtons.add(helpBtn);

        Map<String, Object> menu = new LinkedHashMap<>();
        menu.put("button", allButtons);
        return objectMapper.writeValueAsString(menu);
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