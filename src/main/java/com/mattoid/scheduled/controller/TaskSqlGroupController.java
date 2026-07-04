package com.mattoid.scheduled.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mattoid.scheduled.common.PageResult;
import com.mattoid.scheduled.common.PageUtil;
import com.mattoid.scheduled.common.Result;
import com.mattoid.scheduled.dto.PageQuery;
import com.mattoid.scheduled.entity.TaskSqlGroup;
import com.mattoid.scheduled.service.TaskSqlGroupService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/task-sql-group")
public class TaskSqlGroupController {

    private final TaskSqlGroupService taskSqlGroupService;

    public TaskSqlGroupController(TaskSqlGroupService taskSqlGroupService) {
        this.taskSqlGroupService = taskSqlGroupService;
    }

    @PreAuthorize("hasAuthority('taskSqlGroup:view')")
    @GetMapping("/list")
    public Result<List<TaskSqlGroup>> list() {
        return Result.ok(taskSqlGroupService.listActive());
    }

    @PreAuthorize("hasAuthority('taskSqlGroup:view')")
    @GetMapping("/page")
    public Result<PageResult<TaskSqlGroup>> page(PageQuery query,
                                                 @RequestParam(required = false) String groupName) {
        LambdaQueryWrapper<TaskSqlGroup> wrapper = new LambdaQueryWrapper<TaskSqlGroup>()
                .like(StringUtils.hasText(groupName), TaskSqlGroup::getGroupName, groupName)
                .orderByDesc(TaskSqlGroup::getCreateTime);
        Page<TaskSqlGroup> page = taskSqlGroupService.page(
                new Page<>(query.getCurrent(), query.getSize()),
                wrapper
        );
        return Result.ok(PageUtil.convert(page));
    }

    @PreAuthorize("hasAuthority('taskSqlGroup:view')")
    @GetMapping("/{id}")
    public Result<TaskSqlGroup> getById(@PathVariable Long id) {
        return Result.ok(taskSqlGroupService.getById(id));
    }

    @PreAuthorize("hasAuthority('taskSqlGroup:create')")
    @PostMapping
    public Result<Boolean> create(@RequestBody TaskSqlGroup group) {
        return Result.ok(taskSqlGroupService.save(group));
    }

    @PreAuthorize("hasAuthority('taskSqlGroup:edit')")
    @PutMapping("/{id}")
    public Result<Boolean> update(@PathVariable Long id, @RequestBody TaskSqlGroup group) {
        group.setId(id);
        return Result.ok(taskSqlGroupService.updateById(group));
    }

    @PreAuthorize("hasAuthority('taskSqlGroup:delete')")
    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.ok(taskSqlGroupService.removeById(id));
    }
}
