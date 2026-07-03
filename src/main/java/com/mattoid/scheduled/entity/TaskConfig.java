package com.mattoid.scheduled.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("task_config")
public class TaskConfig extends BaseEntity {

    private String taskName;
    private String taskCode;
    private String description;

    /**
     * ONCE / CRON
     */
    private String triggerType;

    /**
     * CRON expression or fixed datetime string
     */
    private String triggerConfig;

    private Long emailConfigId;

    private String recipientIds;

    /**
     * 逗号分隔的收件人群组 ID
     */
    private String recipientGroupIds;

    /**
     * ENABLE / DISABLE
     */
    private String status;

    private String fileNamePattern;

    private String emailSubject;

    private String emailBody;

    private Long weComAppConfigId;

    private Long weComBotConfigId;

    private String weComToUser;

    public Long getWeComAppConfigId() {
        return weComAppConfigId;
    }

    public void setWeComAppConfigId(Long weComAppConfigId) {
        this.weComAppConfigId = weComAppConfigId;
    }

    public Long getWeComBotConfigId() {
        return weComBotConfigId;
    }

    public void setWeComBotConfigId(Long weComBotConfigId) {
        this.weComBotConfigId = weComBotConfigId;
    }

    public String getWeComToUser() {
        return weComToUser;
    }

    public void setWeComToUser(String weComToUser) {
        this.weComToUser = weComToUser;
    }
}
