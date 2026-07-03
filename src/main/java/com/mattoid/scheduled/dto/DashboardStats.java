package com.mattoid.scheduled.dto;

import lombok.Data;

@Data
public class DashboardStats {

    private Long taskCount;
    private Long datasourceCount;
    private Long emailConfigCount;
    private Long templateCount;
    private Long todayLogCount;
    private Long successLogCount;
    private Long failedLogCount;
}
