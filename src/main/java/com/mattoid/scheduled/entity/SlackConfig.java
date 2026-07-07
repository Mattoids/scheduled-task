package com.mattoid.scheduled.entity;

import lombok.Data;

@Data
public class SlackConfig {

    private String configName;

    /**
     * Slack Incoming Webhook URL
     */
    private String webhookUrl;

    /**
     * 频道名称，如 #general；若为空则使用 webhook 默认频道
     */
    private String channel;

    /**
     * 发送者显示名称
     */
    private String username;

    private Integer status;
}
