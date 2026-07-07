package com.mattoid.scheduled.entity;

import lombok.Data;

@Data
public class FeishuConfig {

    private String configName;

    /**
     * 飞书群机器人 Webhook 地址
     */
    private String webhookUrl;

    /**
     * 加签密钥
     */
    private String secret;

    private Integer status;
}
