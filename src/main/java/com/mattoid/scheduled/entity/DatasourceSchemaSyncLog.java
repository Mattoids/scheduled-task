package com.mattoid.scheduled.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("datasource_schema_sync_log")
public class DatasourceSchemaSyncLog extends BaseEntity {

    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAIL = "FAIL";

    private Long datasourceId;

    private String datasourceName;

    /** RUNNING / SUCCESS / FAIL */
    private String status;

    private Integer tableCount;

    private Long docId;

    private String docTitle;

    private String errorMessage;

    private Long durationMs;

    private LocalDateTime startTime;

    private LocalDateTime endTime;
}
