package com.mattoid.scheduled.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wecom_app_config")
// 通知配置 JSON 可能携带 mode/botId/botSecret 等智能机器人字段（与 WeComIntelligentBotConfig 共用结构），
// 反序列化为应用配置时忽略未知字段，避免长链/回调模式配置互相命中时直接抛 UnrecognizedPropertyException。
@JsonIgnoreProperties(ignoreUnknown = true)
public class WeComAppConfig extends BaseEntity {

    private String configName;
    private String corpId;
    private Integer agentId;
    private String secret;
    private String token;
    private String aesKey;
    private String proxyUrl;
    private Integer status;
    private String menuJson;
}
