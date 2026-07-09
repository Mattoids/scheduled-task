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

    /**
     * ENABLE / DISABLE
     */
    private String status;

    /**
     * 排序权重，值越大越靠前
     */
    private Integer sortOrder;

    /**
     * 是否加入企业微信应用菜单：1 是，0 否
     */
    private Integer inWecomMenu;

    /**
     * SQL / CRAWL
     */
    private String taskType;

}
