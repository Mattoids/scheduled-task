package com.mattoid.scheduled.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mattoid.scheduled.common.PageResult;
import com.mattoid.scheduled.common.PageUtil;
import com.mattoid.scheduled.common.Result;
import com.mattoid.scheduled.dto.PageQuery;
import com.mattoid.scheduled.entity.TaskSqlConfig;
import com.mattoid.scheduled.service.TaskSqlConfigService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/task-sql")
public class TaskSqlConfigController {

    private final TaskSqlConfigService taskSqlConfigService;

    public TaskSqlConfigController(TaskSqlConfigService taskSqlConfigService) {
        this.taskSqlConfigService = taskSqlConfigService;
    }

    @PreAuthorize("hasAuthority('task:view')")
    @GetMapping("/page")
    public Result<PageResult<TaskSqlConfig>> page(PageQuery query) {
        Page<TaskSqlConfig> page = taskSqlConfigService.page(new Page<>(query.getCurrent(), query.getSize()));
        return Result.ok(PageUtil.convert(page));
    }

    @PreAuthorize("hasAuthority('task:view')")
    @GetMapping("/list")
    public Result<List<TaskSqlConfig>> list() {
        return Result.ok(taskSqlConfigService.lambdaQuery()
                .eq(TaskSqlConfig::getStatus, 1)
                .list());
    }

    @PreAuthorize("hasAuthority('task:view')")
    @GetMapping("/{id}")
    public Result<TaskSqlConfig> detail(@PathVariable Long id) {
        return Result.ok(taskSqlConfigService.getById(id));
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
}
