package com.mattoid.scheduled.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("notification_config")
public class NotificationConfig extends BaseEntity {

    private String configName;

    private String configCode;

    private String configType;
    private String configJson;
    private Integer status;
}
