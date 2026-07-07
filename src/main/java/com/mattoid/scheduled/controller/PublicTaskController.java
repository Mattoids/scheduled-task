package com.mattoid.scheduled.controller;

import com.mattoid.scheduled.common.Result;
import com.mattoid.scheduled.service.TaskConfigService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StringUtils;

@Slf4j
@RestController
@RequestMapping("/api/public")
public class PublicTaskController {

    @Value("${report.api.key}")
    private String apiKey;

    private final TaskConfigService taskConfigService;

    public PublicTaskController(TaskConfigService taskConfigService) {
        this.taskConfigService = taskConfigService;
    }

    @PostMapping("/tasks/{taskId}/trigger")
    public Result<Void> triggerTask(@PathVariable Long taskId, HttpServletRequest request) {
        String providedKey = request.getHeader("X-API-KEY");
        if (!StringUtils.hasText(apiKey)) {
            log.warn("API Key 未配置，拒绝外部触发请求");
            return Result.error(503, "API Key 未配置");
        }
        if (!apiKey.equals(providedKey)) {
            log.warn("API Key 校验失败: taskId={}", taskId);
            return Result.error(401, "API Key 无效");
        }
        taskConfigService.triggerTask(taskId);
        log.info("外部 API 触发任务成功: taskId={}", taskId);
        return Result.ok();
    }
}
