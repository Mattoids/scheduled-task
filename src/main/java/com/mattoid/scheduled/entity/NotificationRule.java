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
     * 对应 notification_config 的 id，保留兼容
     */
    private Long configId;

    /**
     * 对应 notification_config 的编码，优先使用
     */
    private String configCode;

    /**
     * 指定任务 ID，保留兼容
     */
    private Long taskId;

    /**
     * 指定任务编码，优先使用
     */
    private String taskCode;

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

    /**
     * 存储配置 ID，为空时企业微信文件直接发送，不为空时上传到存储系统后发链接
     */
    private Long storageConfigId;

    private Integer enabled;
}
