package com.mattoid.scheduled.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mattoid.scheduled.common.PageResult;
import com.mattoid.scheduled.common.Result;
import com.mattoid.scheduled.dto.ChangeStatusRequest;
import com.mattoid.scheduled.dto.PageQuery;
import com.mattoid.scheduled.entity.TaskConfig;
import com.mattoid.scheduled.entity.TaskLog;
import com.mattoid.scheduled.mapper.TaskLogMapper;
import com.mattoid.scheduled.service.TaskConfigService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/task")
public class TaskConfigController {

    private final TaskConfigService taskConfigService;
    private final TaskLogMapper taskLogMapper;

    public TaskConfigController(TaskConfigService taskConfigService,
                                TaskLogMapper taskLogMapper) {
        this.taskConfigService = taskConfigService;
        this.taskLogMapper = taskLogMapper;
    }

    @PreAuthorize("hasAuthority('task:view')")
    @GetMapping("/page")
    public Result<PageResult<TaskConfig>> page(PageQuery query) {
        Page<TaskConfig> page = taskConfigService.page(new Page<>(query.getCurrent(), query.getSize()));
        PageResult<TaskConfig> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setPages(page.getPages());
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        result.setRecords(page.getRecords());
        return Result.ok(result);
    }

    @PreAuthorize("hasAuthority('task:view')")
    @GetMapping("/{id}")
    public Result<TaskConfig> detail(@PathVariable Long id) {
        return Result.ok(taskConfigService.getById(id));
    }

    @PreAuthorize("hasAuthority('task:create')")
    @PostMapping
    public Result<Boolean> create(@RequestBody TaskConfig task) throws Exception {
        return Result.ok(taskConfigService.saveOrUpdateTask(task));
    }

    @PreAuthorize("hasAuthority('task:edit')")
    @PutMapping("/{id}")
    public Result<Boolean> update(@PathVariable Long id, @RequestBody TaskConfig task) throws Exception {
        task.setId(id);
        return Result.ok(taskConfigService.saveOrUpdateTask(task));
    }

    @PreAuthorize("hasAuthority('task:edit')")
    @PutMapping("/{id}/status")
    public Result<Boolean> updateStatus(@PathVariable Long id, @RequestBody ChangeStatusRequest request) throws Exception {
        return Result.ok(taskConfigService.updateStatus(id, request.getStatus()));
    }

    @PreAuthorize("hasAuthority('task:delete')")
    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) throws Exception {
        return Result.ok(taskConfigService.removeTask(id));
    }

    @PreAuthorize("hasAuthority('task:trigger')")
    @PostMapping("/{id}/trigger")
    public Result<String> trigger(@PathVariable Long id) {
        taskConfigService.triggerTask(id);
        return Result.ok("任务已触发");
    }

    @PreAuthorize("hasAuthority('log:view')")
    @GetMapping("/{taskId}/logs")
    public Result<PageResult<TaskLog>> logs(@PathVariable Long taskId, PageQuery query) {
        Page<TaskLog> page = taskLogMapper.selectPage(
                new Page<>(query.getCurrent(), query.getSize()),
                new LambdaQueryWrapper<TaskLog>().eq(TaskLog::getTaskId, taskId).orderByDesc(TaskLog::getCreateTime)
        );
        PageResult<TaskLog> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setPages(page.getPages());
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        result.setRecords(page.getRecords());
        return Result.ok(result);
    }
}
