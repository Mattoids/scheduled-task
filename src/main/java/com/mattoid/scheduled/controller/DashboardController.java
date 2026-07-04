package com.mattoid.scheduled.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mattoid.scheduled.common.Result;
import com.mattoid.scheduled.dto.DashboardStats;
import com.mattoid.scheduled.entity.TaskConfig;
import com.mattoid.scheduled.entity.TaskLog;
import com.mattoid.scheduled.mapper.DatasourceConfigMapper;
import com.mattoid.scheduled.mapper.NotificationConfigMapper;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final TaskConfigMapper taskConfigMapper;
    private final DatasourceConfigMapper datasourceConfigMapper;
    private final NotificationConfigMapper notificationConfigMapper;
    private final ReportTemplateMapper reportTemplateMapper;
    private final TaskLogMapper taskLogMapper;

    public DashboardController(TaskConfigMapper taskConfigMapper,
                               DatasourceConfigMapper datasourceConfigMapper,
                               NotificationConfigMapper notificationConfigMapper,
                               ReportTemplateMapper reportTemplateMapper,
                               TaskLogMapper taskLogMapper) {
        this.taskConfigMapper = taskConfigMapper;
        this.datasourceConfigMapper = datasourceConfigMapper;
        this.notificationConfigMapper = notificationConfigMapper;
        this.reportTemplateMapper = reportTemplateMapper;
        this.taskLogMapper = taskLogMapper;
    }

    @PreAuthorize("hasAuthority('task:view')")
    @GetMapping("/stats")
    public Result<DashboardStats> stats() {
        DashboardStats stats = new DashboardStats();
        stats.setTaskCount((long) taskConfigMapper.selectCount(null));
        stats.setDatasourceCount((long) datasourceConfigMapper.selectCount(null));
        stats.setNotificationConfigCount((long) notificationConfigMapper.selectCount(null));
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

        // 任务状态分布
        Map<String, Long> taskStatusStats = new HashMap<>();
        taskStatusStats.put("ENABLE", (long) taskConfigMapper.selectCount(
                new LambdaQueryWrapper<TaskConfig>().eq(TaskConfig::getStatus, "ENABLE")));
        taskStatusStats.put("DISABLE", (long) taskConfigMapper.selectCount(
                new LambdaQueryWrapper<TaskConfig>().eq(TaskConfig::getStatus, "DISABLE")));
        stats.setTaskStatusStats(taskStatusStats);

        // 今日执行状态分布
        Map<String, Long> todayStatusStats = new HashMap<>();
        for (String status : new String[]{"SUCCESS", "FAILED", "RUNNING"}) {
            todayStatusStats.put(status, (long) taskLogMapper.selectCount(
                    new LambdaQueryWrapper<TaskLog>()
                            .ge(TaskLog::getCreateTime, todayStart)
                            .le(TaskLog::getCreateTime, todayEnd)
                            .eq(TaskLog::getStatus, status)));
        }
        stats.setTodayStatusStats(todayStatusStats);

        // 最近执行日志（最近 10 条）
        List<TaskLog> recentLogs = taskLogMapper.selectList(
                new LambdaQueryWrapper<TaskLog>()
                        .orderByDesc(TaskLog::getCreateTime)
                        .last("LIMIT 10")
        );
        List<Long> taskIds = recentLogs.stream()
                .map(TaskLog::getTaskId)
                .distinct()
                .collect(Collectors.toList());
        Map<Long, String> taskNameMap = taskIds.isEmpty() ? new HashMap<>()
                : taskConfigMapper.selectBatchIds(taskIds).stream()
                        .collect(Collectors.toMap(TaskConfig::getId, TaskConfig::getTaskName));

        stats.setRecentLogs(recentLogs.stream().map(log -> {
            DashboardStats.RecentTaskLog item = new DashboardStats.RecentTaskLog();
            item.setId(log.getId());
            item.setTaskId(log.getTaskId());
            item.setTaskName(taskNameMap.getOrDefault(log.getTaskId(), "未知任务"));
            item.setTriggerMode(log.getTriggerMode());
            item.setStatus(log.getStatus());
            item.setStartTime(log.getStartTime());
            item.setEndTime(log.getEndTime());
            item.setResultMessage(log.getResultMessage());
            return item;
        }).collect(Collectors.toList()));

        return Result.ok(stats);
    }
}
