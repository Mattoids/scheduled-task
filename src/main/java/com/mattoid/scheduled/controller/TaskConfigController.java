package com.mattoid.scheduled.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mattoid.scheduled.audit.OperationAudit;
import com.mattoid.scheduled.common.PageResult;
import com.mattoid.scheduled.common.Result;
import com.mattoid.scheduled.config.WeComMenuRegistrar;
import com.mattoid.scheduled.dto.ChangeStatusRequest;
import com.mattoid.scheduled.dto.PageQuery;
import com.mattoid.scheduled.dto.TaskConfigRequest;
import com.mattoid.scheduled.entity.TaskConfig;
import com.mattoid.scheduled.entity.TaskLog;
import com.mattoid.scheduled.mapper.TaskLogMapper;
import com.mattoid.scheduled.service.TaskConfigService;
import com.mattoid.scheduled.service.TaskDependencyService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/task")
public class TaskConfigController {

    private final TaskConfigService taskConfigService;
    private final TaskLogMapper taskLogMapper;
    private final TaskDependencyService taskDependencyService;
    private final WeComMenuRegistrar weComMenuRegistrar;

    public TaskConfigController(TaskConfigService taskConfigService,
                                TaskLogMapper taskLogMapper,
                                TaskDependencyService taskDependencyService,
                                WeComMenuRegistrar weComMenuRegistrar) {
        this.taskConfigService = taskConfigService;
        this.taskLogMapper = taskLogMapper;
        this.taskDependencyService = taskDependencyService;
        this.weComMenuRegistrar = weComMenuRegistrar;
    }

    @PreAuthorize("hasAuthority('task:view')")
    @GetMapping("/page")
    public Result<PageResult<TaskConfig>> page(PageQuery query,
                                               @RequestParam(required = false) String taskName,
                                               @RequestParam(required = false) String taskCode) {
        LambdaQueryWrapper<TaskConfig> wrapper = new LambdaQueryWrapper<TaskConfig>()
                .like(StringUtils.hasText(taskName), TaskConfig::getTaskName, taskName)
                .like(StringUtils.hasText(taskCode), TaskConfig::getTaskCode, taskCode)
                .orderByDesc(TaskConfig::getSortOrder)
                .orderByDesc(TaskConfig::getCreateTime);
        Page<TaskConfig> page = taskConfigService.page(new Page<>(query.getCurrent(), query.getSize()), wrapper);
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
    public Result<TaskConfigRequest> detail(@PathVariable Long id) {
        TaskConfig task = taskConfigService.getById(id);
        if (task == null) {
            return Result.error("任务不存在");
        }
        TaskConfigRequest request = new TaskConfigRequest();
        request.setTask(task);
        request.setSqlCodes(taskConfigService.getTaskSqlCodes(task.getTaskCode()));
        request.setCrawlCodes(taskConfigService.getTaskCrawlCodes(task.getTaskCode()));
        request.setDependencyIds(taskDependencyService.getDependencyIds(id));
        return Result.ok(request);
    }

    @OperationAudit(operationType = "CREATE", resourceType = "TASK")
    @PreAuthorize("hasAuthority('task:create')")
    @PostMapping
    public Result<Boolean> create(@RequestBody TaskConfigRequest request) throws Exception {
        return Result.ok(taskConfigService.saveOrUpdateTask(request.getTask(), request.getSqlCodes(),
                request.getCrawlCodes(), request.getDependencyIds()));
    }

    @OperationAudit(operationType = "UPDATE", resourceType = "TASK")
    @PreAuthorize("hasAuthority('task:edit')")
    @PutMapping("/{id}")
    public Result<Boolean> update(@PathVariable Long id, @RequestBody TaskConfigRequest request) throws Exception {
        TaskConfig task = request.getTask();
        if (task == null) {
            task = new TaskConfig();
        }
        task.setId(id);
        return Result.ok(taskConfigService.saveOrUpdateTask(task, request.getSqlCodes(),
                request.getCrawlCodes(), request.getDependencyIds()));
    }

    @OperationAudit(operationType = "UPDATE", resourceType = "TASK")
    @PreAuthorize("hasAuthority('task:edit')")
    @PutMapping("/{id}/status")
    public Result<Boolean> updateStatus(@PathVariable Long id, @RequestBody ChangeStatusRequest request) throws Exception {
        return Result.ok(taskConfigService.updateStatus(id, request.getStatus()));
    }

    @OperationAudit(operationType = "DELETE", resourceType = "TASK")
    @PreAuthorize("hasAuthority('task:delete')")
    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) throws Exception {
        return Result.ok(taskConfigService.removeTask(id));
    }

    @OperationAudit(operationType = "EXECUTE", resourceType = "TASK")
    @PreAuthorize("hasAuthority('task:trigger')")
    @PostMapping("/{id}/trigger")
    public Result<String> trigger(@PathVariable Long id) {
        taskConfigService.triggerTask(id);
        return Result.ok("任务已提交执行");
    }

    @OperationAudit(operationType = "EXECUTE", resourceType = "TASK")
    @PreAuthorize("hasAuthority('task:edit')")
    @PostMapping("/sync-wecom-menu")
    public Result<List<WeComMenuRegistrar.MenuSyncResult>> syncWeComMenu() {
        return Result.ok(weComMenuRegistrar.syncMenus());
    }

    @PreAuthorize("hasAuthority('task:view')")
    @GetMapping("/{id}/dependencies")
    public Result<List<Long>> dependencies(@PathVariable Long id) {
        return Result.ok(taskDependencyService.getDependencyIds(id));
    }

    @OperationAudit(operationType = "UPDATE", resourceType = "TASK_DEPENDENCY")
    @PreAuthorize("hasAuthority('task:edit')")
    @PutMapping("/{id}/dependencies")
    public Result<Boolean> updateDependencies(@PathVariable Long id, @RequestBody List<Long> dependencyIds) {
        taskDependencyService.saveDependencies(id, dependencyIds);
        return Result.ok(true);
    }

    @PreAuthorize("hasAuthority('task:view')")
    @GetMapping("/{id}/dependents")
    public Result<List<Long>> dependents(@PathVariable Long id) {
        return Result.ok(taskDependencyService.getDependentIds(id));
    }

    @PreAuthorize("hasAuthority('task:view')")
    @GetMapping("/{id}/dependency-chain")
    public Result<List<Long>> dependencyChain(@PathVariable Long id) {
        return Result.ok(taskDependencyService.topologicalSort(id));
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
