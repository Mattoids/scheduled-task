package com.mattoid.scheduled.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mattoid.scheduled.audit.OperationAudit;
import com.mattoid.scheduled.common.PageResult;
import com.mattoid.scheduled.common.PageUtil;
import com.mattoid.scheduled.common.Result;
import com.mattoid.scheduled.dto.PageQuery;
import com.mattoid.scheduled.entity.NotificationConfig;
import com.mattoid.scheduled.entity.NotificationRule;
import com.mattoid.scheduled.entity.TaskConfig;
import com.mattoid.scheduled.service.NotificationConfigService;
import com.mattoid.scheduled.service.NotificationRuleService;
import com.mattoid.scheduled.service.TaskConfigService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notification-rule")
public class NotificationRuleController {

    private final NotificationRuleService notificationRuleService;
    private final NotificationConfigService notificationConfigService;
    private final TaskConfigService taskConfigService;

    public NotificationRuleController(NotificationRuleService notificationRuleService,
                                      NotificationConfigService notificationConfigService,
                                      TaskConfigService taskConfigService) {
        this.notificationRuleService = notificationRuleService;
        this.notificationConfigService = notificationConfigService;
        this.taskConfigService = taskConfigService;
    }

    @PreAuthorize("hasAuthority('notificationRule:view')")
    @GetMapping("/list")
    public Result<List<NotificationRule>> list(@RequestParam(required = false) String eventType) {
        LambdaQueryWrapper<NotificationRule> wrapper = new LambdaQueryWrapper<NotificationRule>()
                .eq(NotificationRule::getEnabled, 1)
                .orderByDesc(NotificationRule::getCreateTime);
        if (StringUtils.hasText(eventType)) {
            wrapper.eq(NotificationRule::getEventType, eventType);
        }
        return Result.ok(notificationRuleService.list(wrapper));
    }

    @PreAuthorize("hasAuthority('notificationRule:view')")
    @GetMapping("/page")
    public Result<PageResult<NotificationRule>> page(PageQuery query,
                                                     @RequestParam(required = false) String eventType,
                                                     @RequestParam(required = false) String channel,
                                                     @RequestParam(required = false) String taskCode) {
        LambdaQueryWrapper<NotificationRule> wrapper = new LambdaQueryWrapper<NotificationRule>()
                .eq(StringUtils.hasText(eventType), NotificationRule::getEventType, eventType)
                .eq(StringUtils.hasText(channel), NotificationRule::getChannel, channel)
                .eq(StringUtils.hasText(taskCode), NotificationRule::getTaskCode, taskCode)
                .orderByDesc(NotificationRule::getCreateTime);
        Page<NotificationRule> page = notificationRuleService.page(new Page<>(query.getCurrent(), query.getSize()), wrapper);
        return Result.ok(PageUtil.convert(page));
    }

    @OperationAudit(operationType = "CREATE", resourceType = "NOTIFICATION_RULE")
    @PreAuthorize("hasAuthority('notificationRule:create')")
    @PostMapping
    public Result<Boolean> create(@RequestBody NotificationRule rule) {
        resolveCodeRelations(rule);
        return Result.ok(notificationRuleService.save(rule));
    }

    @OperationAudit(operationType = "UPDATE", resourceType = "NOTIFICATION_RULE")
    @PreAuthorize("hasAuthority('notificationRule:edit')")
    @PutMapping("/{id}")
    public Result<Boolean> update(@PathVariable Long id, @RequestBody NotificationRule rule) {
        resolveCodeRelations(rule);
        boolean updated = notificationRuleService.update(new LambdaUpdateWrapper<NotificationRule>()
                .set(NotificationRule::getEventType, rule.getEventType())
                .set(NotificationRule::getChannel, rule.getChannel())
                .set(NotificationRule::getConfigId, rule.getConfigId())
                .set(NotificationRule::getConfigCode, rule.getConfigCode())
                .set(NotificationRule::getTaskId, rule.getTaskId())
                .set(NotificationRule::getTaskCode, rule.getTaskCode())
                .set(NotificationRule::getRecipientIds, rule.getRecipientIds())
                .set(NotificationRule::getRecipientGroupIds, rule.getRecipientGroupIds())
                .set(NotificationRule::getWecomToUser, rule.getWecomToUser())
                .set(NotificationRule::getSubject, rule.getSubject())
                .set(NotificationRule::getBody, rule.getBody())
                .set(NotificationRule::getContent, rule.getContent())
                .set(NotificationRule::getAiOptimizeNotify, rule.getAiOptimizeNotify())
                .set(NotificationRule::getAiConfigId, rule.getAiConfigId())
                .set(NotificationRule::getStorageConfigId, rule.getStorageConfigId())
                .set(NotificationRule::getEnabled, rule.getEnabled())
                .eq(NotificationRule::getId, id));
        return Result.ok(updated);
    }

    private void resolveCodeRelations(NotificationRule rule) {
        if (StringUtils.hasText(rule.getConfigCode())) {
            NotificationConfig config = notificationConfigService.getByCode(rule.getConfigCode());
            if (config != null) {
                rule.setConfigId(config.getId());
            }
        }
        if (StringUtils.hasText(rule.getTaskCode())) {
            TaskConfig task = taskConfigService.lambdaQuery()
                    .eq(TaskConfig::getTaskCode, rule.getTaskCode())
                    .one();
            if (task != null) {
                rule.setTaskId(task.getId());
            }
        }
    }

    @OperationAudit(operationType = "DELETE", resourceType = "NOTIFICATION_RULE")
    @PreAuthorize("hasAuthority('notificationRule:delete')")
    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.ok(notificationRuleService.removeById(id));
    }
}
