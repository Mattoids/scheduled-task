package com.mattoid.scheduled.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wecom_app_config")
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
