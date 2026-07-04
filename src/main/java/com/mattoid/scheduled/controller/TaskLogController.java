package com.mattoid.scheduled.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mattoid.scheduled.common.PageResult;
import com.mattoid.scheduled.common.Result;
import com.mattoid.scheduled.dto.PageQuery;
import com.mattoid.scheduled.entity.TaskLog;
import com.mattoid.scheduled.mapper.TaskLogMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/task-log")
public class TaskLogController {

    private final TaskLogMapper taskLogMapper;

    public TaskLogController(TaskLogMapper taskLogMapper) {
        this.taskLogMapper = taskLogMapper;
    }

    @PreAuthorize("hasAuthority('log:view')")
    @GetMapping("/page")
    public Result<PageResult<TaskLog>> page(PageQuery query,
                                            @RequestParam(required = false) String status) {
        LambdaQueryWrapper<TaskLog> wrapper = new LambdaQueryWrapper<TaskLog>()
                .eq(StringUtils.hasText(status), TaskLog::getStatus, status)
                .orderByDesc(TaskLog::getCreateTime);
        Page<TaskLog> page = taskLogMapper.selectPage(
                new Page<>(query.getCurrent(), query.getSize()),
                wrapper
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
