package com.mattoid.scheduled.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class WeComIntelligentBotConfig extends WeComAppConfig {

    /**
     * 连接模式：LONGCHAIN / CALLBACK
     */
    private String mode;

    /**
     * 智能机器人 ID（长链模式）
     */
    private String botId;

    /**
     * 智能机器人 Secret（长链模式）
     */
    private String botSecret;
}