package com.mattoid.scheduled.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class DashboardStats {

    private Long taskCount;
    private Long datasourceCount;
    private Long notificationConfigCount;
    private Long templateCount;
    private Long todayLogCount;
    private Long successLogCount;
    private Long failedLogCount;

    /**
     * 任务状态分布：ENABLE / DISABLE
     */
    private Map<String, Long> taskStatusStats;

    /**
     * 今日执行状态分布：SUCCESS / FAILED / RUNNING
     */
    private Map<String, Long> todayStatusStats;

    /**
     * 最近执行日志
     */
    private List<RecentTaskLog> recentLogs;

    @Data
    public static class RecentTaskLog {
        private Long id;
        private Long taskId;
        private String taskName;
        private String triggerMode;
        private String status;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private String resultMessage;
    }
}
