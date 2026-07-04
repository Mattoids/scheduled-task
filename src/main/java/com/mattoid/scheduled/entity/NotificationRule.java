package com.mattoid.scheduled.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("notification_rule")
public class NotificationRule extends BaseEntity {

    /**
     * TASK_SUCCESS / TASK_FAILURE / TASK_COMPLETED
     */
    private String eventType;

    /**
     * EMAIL / WECOM_APP / WECOM_BOT
     */
    private String channel;

    /**
     * 对应 email_config / wecom_app_config / wecom_bot_config 的 id
     */
    private Long configId;

    /**
     * 指定任务 ID，为空表示不固定任务（所有任务都通知）
     */
    private Long taskId;

    /**
     * EMAIL 渠道：收件人 id，逗号分隔
     */
    private String recipientIds;

    /**
     * EMAIL 渠道：收件人群组 id，逗号分隔
     */
    private String recipientGroupIds;

    /**
     * WECOM_APP 渠道：接收人
     */
    private String wecomToUser;

    private String subject;

    private String body;

    /**
     * WeCom 文本模板
     */
    private String content;

    /**
     * 是否启用 AI 优化通知内容：1 启用，0 禁用
     */
    private Integer aiOptimizeNotify;

    /**
     * AI 配置 ID，为空时使用默认配置
     */
    private Long aiConfigId;

    private Integer enabled;
}
