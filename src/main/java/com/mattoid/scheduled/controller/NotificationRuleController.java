package com.mattoid.scheduled.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mattoid.scheduled.common.PageResult;
import com.mattoid.scheduled.common.PageUtil;
import com.mattoid.scheduled.common.Result;
import com.mattoid.scheduled.dto.PageQuery;
import com.mattoid.scheduled.entity.NotificationRule;
import com.mattoid.scheduled.service.NotificationRuleService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notification-rule")
public class NotificationRuleController {

    private final NotificationRuleService notificationRuleService;

    public NotificationRuleController(NotificationRuleService notificationRuleService) {
        this.notificationRuleService = notificationRuleService;
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
                                                     @RequestParam(required = false) Long taskId) {
        LambdaQueryWrapper<NotificationRule> wrapper = new LambdaQueryWrapper<NotificationRule>()
                .eq(StringUtils.hasText(eventType), NotificationRule::getEventType, eventType)
                .eq(StringUtils.hasText(channel), NotificationRule::getChannel, channel)
                .eq(taskId != null, NotificationRule::getTaskId, taskId)
                .orderByDesc(NotificationRule::getCreateTime);
        Page<NotificationRule> page = notificationRuleService.page(new Page<>(query.getCurrent(), query.getSize()), wrapper);
        return Result.ok(PageUtil.convert(page));
    }

    @PreAuthorize("hasAuthority('notificationRule:create')")
    @PostMapping
    public Result<Boolean> create(@RequestBody NotificationRule rule) {
        return Result.ok(notificationRuleService.save(rule));
    }

    @PreAuthorize("hasAuthority('notificationRule:edit')")
    @PutMapping("/{id}")
    public Result<Boolean> update(@PathVariable Long id, @RequestBody NotificationRule rule) {
        rule.setId(id);
        return Result.ok(notificationRuleService.updateById(rule));
    }

    @PreAuthorize("hasAuthority('notificationRule:delete')")
    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.ok(notificationRuleService.removeById(id));
    }
}
