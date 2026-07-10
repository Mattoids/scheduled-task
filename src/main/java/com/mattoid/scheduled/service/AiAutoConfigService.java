package com.mattoid.scheduled.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.mattoid.scheduled.entity.NotificationRule;
import com.mattoid.scheduled.entity.TaskConfig;
import com.mattoid.scheduled.entity.TaskWebCrawlConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;

@Slf4j
@Service
public class AiAutoConfigService {

    private final AiAssistantService aiAssistantService;
    private final TaskConfigService taskConfigService;
    private final TaskWebCrawlConfigService taskWebCrawlConfigService;
    private final NotificationRuleService notificationRuleService;

    public AiAutoConfigService(AiAssistantService aiAssistantService,
                               TaskConfigService taskConfigService,
                               TaskWebCrawlConfigService taskWebCrawlConfigService,
                               NotificationRuleService notificationRuleService) {
        this.aiAssistantService = aiAssistantService;
        this.taskConfigService = taskConfigService;
        this.taskWebCrawlConfigService = taskWebCrawlConfigService;
        this.notificationRuleService = notificationRuleService;
    }

    @Transactional(rollbackFor = Exception.class)
    public AutoConfigResult autoConfigure(String userInput) throws Exception {
        AiAssistantService.NaturalConfigResult generated = aiAssistantService.generateConfig(userInput);
        String type = generated.type();
        JSONObject config = generated.config();

        if ("UNKNOWN".equalsIgnoreCase(type) || config == null || config.isEmpty()) {
            return AutoConfigResult.fail(generated.summary());
        }

        switch (type.toUpperCase()) {
            case "TASK" -> {
                TaskConfig task = JSON.parseObject(config.toJSONString(), TaskConfig.class);
                if (!StringUtils.hasText(task.getTaskCode())) {
                    task.setTaskCode("TASK_" + System.currentTimeMillis());
                }
                if (!StringUtils.hasText(task.getTaskName())) {
                    task.setTaskName("AI 自动生成任务");
                }
                if (!StringUtils.hasText(task.getTaskType())) {
                    task.setTaskType("SQL");
                }
                if (!StringUtils.hasText(task.getTriggerType())) {
                    task.setTriggerType("CRON");
                }
                if (!StringUtils.hasText(task.getTriggerConfig())) {
                    task.setTriggerConfig("0 0 8 * * ?");
                }
                if (!StringUtils.hasText(task.getStatus())) {
                    task.setStatus("DISABLE");
                }
                taskConfigService.saveOrUpdateTask(task, Collections.emptyList(), Collections.emptyList());
                return AutoConfigResult.ok("TASK", task.getId(), "已创建任务：" + task.getTaskName());
            }
            case "CRAWL" -> {
                TaskWebCrawlConfig crawl = JSON.parseObject(config.toJSONString(), TaskWebCrawlConfig.class);
                if (!StringUtils.hasText(crawl.getCrawlCode())) {
                    crawl.setCrawlCode("CRAWL_" + System.currentTimeMillis());
                }
                if (!StringUtils.hasText(crawl.getCrawlName())) {
                    crawl.setCrawlName("AI 自动生成爬取任务");
                }
                if (!StringUtils.hasText(crawl.getRequestMethod())) {
                    crawl.setRequestMethod("GET");
                }
                crawl.setStatus(crawl.getStatus() != null ? crawl.getStatus() : 0);
                taskWebCrawlConfigService.save(crawl);
                return AutoConfigResult.ok("CRAWL", crawl.getId(), "已创建爬取配置：" + crawl.getCrawlName());
            }
            case "NOTIFICATION_RULE" -> {
                NotificationRule rule = JSON.parseObject(config.toJSONString(), NotificationRule.class);
                rule.setEnabled(rule.getEnabled() != null ? rule.getEnabled() : 1);
                notificationRuleService.save(rule);
                return AutoConfigResult.ok("NOTIFICATION_RULE", rule.getId(), "已创建通知规则");
            }
            default -> {
                return AutoConfigResult.fail("不支持的配置类型：" + type);
            }
        }
    }

    public record AutoConfigResult(boolean success, String type, Long id, String message) {
        public static AutoConfigResult ok(String type, Long id, String message) {
            return new AutoConfigResult(true, type, id, message);
        }

        public static AutoConfigResult fail(String message) {
            return new AutoConfigResult(false, null, null, message);
        }
    }
}
