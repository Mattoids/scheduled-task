package com.mattoid.scheduled.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("task_log")
public class TaskLog extends BaseEntity {

    private Long taskId;

    /**
     * MANUAL / AUTO
     */
    private String triggerMode;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    /**
     * RUNNING / SUCCESS / FAILED
     */
    private String status;

    private String resultMessage;
    private String errorMessage;
    private String filePath;
}
