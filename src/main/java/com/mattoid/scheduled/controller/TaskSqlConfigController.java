package com.mattoid.scheduled.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mattoid.scheduled.common.PageResult;
import com.mattoid.scheduled.common.PageUtil;
import com.mattoid.scheduled.common.Result;
import com.mattoid.scheduled.dto.PageQuery;
import com.mattoid.scheduled.entity.TaskSqlConfig;
import com.mattoid.scheduled.entity.TaskSqlGroup;
import com.mattoid.scheduled.service.TaskSqlConfigService;
import com.mattoid.scheduled.service.TaskSqlGroupService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/task-sql")
public class TaskSqlConfigController {

    private final TaskSqlConfigService taskSqlConfigService;
    private final TaskSqlGroupService taskSqlGroupService;

    public TaskSqlConfigController(TaskSqlConfigService taskSqlConfigService,
                                   TaskSqlGroupService taskSqlGroupService) {
        this.taskSqlConfigService = taskSqlConfigService;
        this.taskSqlGroupService = taskSqlGroupService;
    }

    @PreAuthorize("hasAuthority('task:view')")
    @GetMapping("/page")
    public Result<PageResult<TaskSqlConfig>> page(PageQuery query,
                                                  @RequestParam(required = false) String sqlName,
                                                  @RequestParam(required = false) String sqlCode,
                                                  @RequestParam(required = false) Long groupId) {
        LambdaQueryWrapper<TaskSqlConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(sqlName), TaskSqlConfig::getSqlName, sqlName)
                .like(StringUtils.hasText(sqlCode), TaskSqlConfig::getSqlCode, sqlCode)
                .eq(groupId != null, TaskSqlConfig::getGroupId, groupId)
                .orderByDesc(TaskSqlConfig::getCreateTime);
        Page<TaskSqlConfig> page = taskSqlConfigService.page(new Page<>(query.getCurrent(), query.getSize()), wrapper);
        populateGroupNames(page.getRecords());
        return Result.ok(PageUtil.convert(page));
    }

    @PreAuthorize("hasAuthority('task:view')")
    @GetMapping("/list")
    public Result<List<TaskSqlConfig>> list() {
        List<TaskSqlConfig> configs = taskSqlConfigService.lambdaQuery()
                .eq(TaskSqlConfig::getStatus, 1)
                .list();
        populateGroupNames(configs);
        return Result.ok(configs);
    }

    @PreAuthorize("hasAuthority('task:view')")
    @GetMapping("/{id}")
    public Result<TaskSqlConfig> detail(@PathVariable Long id) {
        TaskSqlConfig config = taskSqlConfigService.getById(id);
        if (config != null && config.getGroupId() != null) {
            TaskSqlGroup group = taskSqlGroupService.getById(config.getGroupId());
            config.setTaskSqlGroup(group);
            config.setGroupName(group != null ? group.getGroupName() : null);
        }
        return Result.ok(config);
    }

    @PreAuthorize("hasAuthority('task:create')")
    @PostMapping
    public Result<Boolean> create(@RequestBody TaskSqlConfig config) {
        return Result.ok(taskSqlConfigService.save(config));
    }

    @PreAuthorize("hasAuthority('task:edit')")
    @PutMapping("/{id}")
    public Result<Boolean> update(@PathVariable Long id, @RequestBody TaskSqlConfig config) {
        config.setId(id);
        return Result.ok(taskSqlConfigService.updateById(config));
    }

    @PreAuthorize("hasAuthority('task:delete')")
    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.ok(taskSqlConfigService.removeSqlConfig(id));
    }

    private void populateGroupNames(List<TaskSqlConfig> configs) {
        if (configs == null || configs.isEmpty()) {
            return;
        }
        List<Long> groupIds = configs.stream()
                .map(TaskSqlConfig::getGroupId)
                .distinct()
                .filter(id -> id != null)
                .collect(Collectors.toList());
        if (groupIds.isEmpty()) {
            return;
        }
        Map<Long, TaskSqlGroup> groupMap = taskSqlGroupService.listByIds(groupIds).stream()
                .collect(Collectors.toMap(TaskSqlGroup::getId, g -> g));
        for (TaskSqlConfig config : configs) {
            TaskSqlGroup group = groupMap.get(config.getGroupId());
            if (group != null) {
                config.setTaskSqlGroup(group);
                config.setGroupName(group.getGroupName());
            }
        }
    }
}
