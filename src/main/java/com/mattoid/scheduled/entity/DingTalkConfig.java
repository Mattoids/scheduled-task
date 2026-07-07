package com.mattoid.scheduled.entity;

import lombok.Data;

@Data
public class DingTalkConfig {

    private String configName;

    /**
     * 钉钉群机器人 Webhook 地址
     */
    private String webhookUrl;

    /**
     * 加签密钥
     */
    private String secret;

    /**
     * 可选：被@人的手机号列表，逗号分隔
     */
    private String atMobiles;

    /**
     * 是否@所有人
     */
    private Boolean atAll;

    private Integer status;
}
