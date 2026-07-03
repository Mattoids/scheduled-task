package com.mattoid.scheduled.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wecom_bot_config")
public class WeComBotConfig extends BaseEntity {

    private String configName;
    private String webhookKey;
    private Integer status;
}
