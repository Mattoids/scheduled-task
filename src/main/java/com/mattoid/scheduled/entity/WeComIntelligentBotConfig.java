package com.mattoid.scheduled.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class WeComIntelligentBotConfig extends WeComAppConfig {

    private String botId;
}