package com.mattoid.scheduled.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mattoid.scheduled.audit.OperationAudit;
import com.mattoid.scheduled.common.PageResult;
import com.mattoid.scheduled.common.PageUtil;
import com.mattoid.scheduled.common.Result;
import com.mattoid.scheduled.common.TestConnectionResult;
import com.mattoid.scheduled.dto.PageQuery;
import com.mattoid.scheduled.entity.NotificationConfig;
import com.mattoid.scheduled.service.NotificationConfigService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notification-config")
public class NotificationConfigController {

    private final NotificationConfigService notificationConfigService;

    public NotificationConfigController(NotificationConfigService notificationConfigService) {
        this.notificationConfigService = notificationConfigService;
    }

    @PreAuthorize("hasAuthority('notificationConfig:view')")
    @GetMapping("/page")
    public Result<PageResult<NotificationConfig>> page(PageQuery query,
                                                      @RequestParam(required = false) String configName,
                                                      @RequestParam(required = false) String configType) {
        LambdaQueryWrapper<NotificationConfig> wrapper = new LambdaQueryWrapper<NotificationConfig>()
                .like(StringUtils.hasText(configName), NotificationConfig::getConfigName, configName)
                .eq(StringUtils.hasText(configType), NotificationConfig::getConfigType, configType)
                .orderByDesc(NotificationConfig::getCreateTime);
        Page<NotificationConfig> page = notificationConfigService.page(new Page<>(query.getCurrent(), query.getSize()), wrapper);
        return Result.ok(PageUtil.convert(page));
    }

    @PreAuthorize("hasAuthority('notificationConfig:view')")
    @GetMapping("/{id}")
    public Result<NotificationConfig> detail(@PathVariable Long id) {
        return Result.ok(notificationConfigService.getById(id));
    }

    @OperationAudit(operationType = "CREATE", resourceType = "NOTIFICATION_CONFIG")
    @PreAuthorize("hasAuthority('notificationConfig:create')")
    @PostMapping
    public Result<Boolean> create(@RequestBody NotificationConfig config) {
        return Result.ok(notificationConfigService.saveOrUpdate(config));
    }

    @OperationAudit(operationType = "UPDATE", resourceType = "NOTIFICATION_CONFIG")
    @PreAuthorize("hasAuthority('notificationConfig:edit')")
    @PutMapping("/{id}")
    public Result<Boolean> update(@PathVariable Long id, @RequestBody NotificationConfig config) {
        config.setId(id);
        return Result.ok(notificationConfigService.saveOrUpdate(config));
    }

    @PreAuthorize("hasAuthority('notificationConfig:view')")
    @PostMapping("/test")
    public Result<TestConnectionResult> test(@RequestBody NotificationConfig config) {
        return Result.ok(notificationConfigService.testConnection(config));
    }

    @OperationAudit(operationType = "DELETE", resourceType = "NOTIFICATION_CONFIG")
    @PreAuthorize("hasAuthority('notificationConfig:delete')")
    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.ok(notificationConfigService.removeById(id));
    }
}
