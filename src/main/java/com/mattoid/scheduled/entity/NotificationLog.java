package com.mattoid.scheduled.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("notification_log")
public class NotificationLog extends BaseEntity {

    private Long taskId;

    private String eventType;

    private Long ruleId;

    private String channel;

    private Long configId;

    private String recipient;

    private String content;

    private String status;

    private String errorMessage;

    private Integer retryCount;
}
