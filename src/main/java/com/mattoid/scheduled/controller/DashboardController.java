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
import com.mattoid.scheduled.service.ChartGenerationService;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.FileInputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final TaskConfigMapper taskConfigMapper;
    private final DatasourceConfigMapper datasourceConfigMapper;
    private final NotificationConfigMapper notificationConfigMapper;
    private final ReportTemplateMapper reportTemplateMapper;
    private final TaskLogMapper taskLogMapper;
    private final ChartGenerationService chartGenerationService;

    public DashboardController(TaskConfigMapper taskConfigMapper,
                               DatasourceConfigMapper datasourceConfigMapper,
                               NotificationConfigMapper notificationConfigMapper,
                               ReportTemplateMapper reportTemplateMapper,
                               TaskLogMapper taskLogMapper,
                               ChartGenerationService chartGenerationService) {
        this.taskConfigMapper = taskConfigMapper;
        this.datasourceConfigMapper = datasourceConfigMapper;
        this.notificationConfigMapper = notificationConfigMapper;
        this.reportTemplateMapper = reportTemplateMapper;
        this.taskLogMapper = taskLogMapper;
        this.chartGenerationService = chartGenerationService;
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

    @PreAuthorize("hasAuthority('task:view')")
    @GetMapping("/server-time")
    public Result<Map<String, Object>> serverTime() {
        ZoneId zone = ZoneId.systemDefault();
        ZonedDateTime now = ZonedDateTime.now(zone);
        Map<String, Object> data = new HashMap<>();
        // 毫秒级时间戳，前端用它配合 RTT/2 做网络延迟补偿
        data.put("serverTimeMillis", System.currentTimeMillis());
        // IANA 时区 ID（如 Asia/Shanghai），前端用 Intl 按此时区渲染
        data.put("timeZone", zone.getId());
        // UTC 偏移（如 +08:00），前端格式化为 UTC+8 展示
        data.put("utcOffset", now.getOffset().getId());
        return Result.ok(data);
    }

    @PreAuthorize("hasAuthority('task:view')")
    @GetMapping("/execution-trend")
    public Result<Map<String, Object>> executionTrend(@RequestParam(defaultValue = "7") int days,
                                                      @RequestParam(required = false) Long taskId) {
        if (days < 1 || days > 90) {
            days = 7;
        }
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1L);
        LocalDateTime startTime = LocalDateTime.of(startDate, LocalTime.MIN);
        LocalDateTime endTime = LocalDateTime.of(endDate, LocalTime.MAX);

        LambdaQueryWrapper<TaskLog> wrapper = new LambdaQueryWrapper<TaskLog>()
                .ge(TaskLog::getCreateTime, startTime)
                .le(TaskLog::getCreateTime, endTime);
        if (taskId != null) {
            wrapper.eq(TaskLog::getTaskId, taskId);
        }
        List<TaskLog> logs = taskLogMapper.selectList(wrapper);

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");
        List<String> dates = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            dates.add(startDate.plusDays(i).format(formatter));
        }

        Map<String, Map<String, Long>> dailyStatusCount = new LinkedHashMap<>();
        for (String date : dates) {
            dailyStatusCount.put(date, new LinkedHashMap<>(Map.of("SUCCESS", 0L, "FAILED", 0L, "RUNNING", 0L)));
        }
        for (TaskLog log : logs) {
            if (log.getCreateTime() == null || log.getStatus() == null) {
                continue;
            }
            String dateKey = log.getCreateTime().toLocalDate().format(formatter);
            Map<String, Long> statusMap = dailyStatusCount.get(dateKey);
            if (statusMap != null) {
                statusMap.merge(log.getStatus(), 1L, Long::sum);
            }
        }

        Map<String, List<Long>> statusData = new LinkedHashMap<>();
        statusData.put("SUCCESS", new ArrayList<>());
        statusData.put("FAILED", new ArrayList<>());
        statusData.put("RUNNING", new ArrayList<>());
        for (String date : dates) {
            Map<String, Long> statusMap = dailyStatusCount.get(date);
            statusData.get("SUCCESS").add(statusMap.getOrDefault("SUCCESS", 0L));
            statusData.get("FAILED").add(statusMap.getOrDefault("FAILED", 0L));
            statusData.get("RUNNING").add(statusMap.getOrDefault("RUNNING", 0L));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dates", dates);
        result.put("statusData", statusData);
        return Result.ok(result);
    }

    @PreAuthorize("hasAuthority('task:view')")
    @GetMapping("/execution-trend-chart")
    public ResponseEntity<InputStreamResource> executionTrendChart(@RequestParam(defaultValue = "7") int days,
                                                                   @RequestParam(required = false) Long taskId) throws Exception {
        if (days < 1 || days > 90) {
            days = 7;
        }
        Result<Map<String, Object>> trendResult = executionTrend(days, taskId);
        Map<String, Object> data = trendResult.getData();
        @SuppressWarnings("unchecked")
        List<String> dates = (List<String>) data.get("dates");
        @SuppressWarnings("unchecked")
        Map<String, List<Long>> statusData = (Map<String, List<Long>>) data.get("statusData");

        List<Map<String, Object>> chartData = new ArrayList<>();
        for (int i = 0; i < dates.size(); i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", dates.get(i));
            row.put("SUCCESS", statusData.get("SUCCESS").get(i));
            row.put("FAILED", statusData.get("FAILED").get(i));
            row.put("RUNNING", statusData.get("RUNNING").get(i));
            chartData.add(row);
        }

        File chartFile = chartGenerationService.generateChart(chartData, "LINE", "任务执行趋势");
        if (chartFile == null || !chartFile.exists()) {
            return ResponseEntity.notFound().build();
        }
        InputStreamResource resource = new InputStreamResource(new FileInputStream(chartFile));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=execution-trend.png")
                .contentType(MediaType.IMAGE_PNG)
                .contentLength(chartFile.length())
                .body(resource);
    }
}
