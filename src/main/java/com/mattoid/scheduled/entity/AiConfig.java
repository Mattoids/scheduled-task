package com.mattoid.scheduled.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_config")
public class AiConfig extends BaseEntity {

    private String configName;

    /**
     * OPENAI / ANTHROPIC / AZURE_OPENAI / OLLAMA / CUSTOM
     */
    private String provider;

    private String apiKey;

    private String baseUrl;

    private String model;

    private Double temperature;

    private Integer maxTokens;

    private Integer timeoutSeconds;

    private Integer isDefault;

    private Integer status;

    /**
     * 系统提示词，用于定义 AI 角色、功能等
     */
    private String systemPrompt;

    private String remark;
}
