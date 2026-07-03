package com.mattoid.scheduled.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mattoid.scheduled.common.Result;
import com.mattoid.scheduled.dto.DashboardStats;
import com.mattoid.scheduled.entity.TaskLog;
import com.mattoid.scheduled.mapper.DatasourceConfigMapper;
import com.mattoid.scheduled.mapper.EmailConfigMapper;
import com.mattoid.scheduled.mapper.ReportTemplateMapper;
import com.mattoid.scheduled.mapper.TaskConfigMapper;
import com.mattoid.scheduled.mapper.TaskLogMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final TaskConfigMapper taskConfigMapper;
    private final DatasourceConfigMapper datasourceConfigMapper;
    private final EmailConfigMapper emailConfigMapper;
    private final ReportTemplateMapper reportTemplateMapper;
    private final TaskLogMapper taskLogMapper;

    public DashboardController(TaskConfigMapper taskConfigMapper,
                               DatasourceConfigMapper datasourceConfigMapper,
                               EmailConfigMapper emailConfigMapper,
                               ReportTemplateMapper reportTemplateMapper,
                               TaskLogMapper taskLogMapper) {
        this.taskConfigMapper = taskConfigMapper;
        this.datasourceConfigMapper = datasourceConfigMapper;
        this.emailConfigMapper = emailConfigMapper;
        this.reportTemplateMapper = reportTemplateMapper;
        this.taskLogMapper = taskLogMapper;
    }

    @PreAuthorize("hasAuthority('task:view')")
    @GetMapping("/stats")
    public Result<DashboardStats> stats() {
        DashboardStats stats = new DashboardStats();
        stats.setTaskCount((long) taskConfigMapper.selectCount(null));
        stats.setDatasourceCount((long) datasourceConfigMapper.selectCount(null));
        stats.setEmailConfigCount((long) emailConfigMapper.selectCount(null));
        stats.setTemplateCount((long) reportTemplateMapper.selectCount(null));

        LocalDateTime todayStart = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        LocalDateTime todayEnd = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);
        stats.setTodayLogCount(taskLogMapper.selectCount(
                new LambdaQueryWrapper<TaskLog>()
                        .ge(TaskLog::getCreateTime, todayStart)
                        .le(TaskLog::getCreateTime, todayEnd)
        ));
        stats.setSuccessLogCount(taskLogMapper.selectCount(
                new LambdaQueryWrapper<TaskLog>()
                        .eq(TaskLog::getStatus, "SUCCESS")
        ));
        stats.setFailedLogCount(taskLogMapper.selectCount(
                new LambdaQueryWrapper<TaskLog>()
                        .eq(TaskLog::getStatus, "FAILED")
        ));
        return Result.ok(stats);
    }
}
