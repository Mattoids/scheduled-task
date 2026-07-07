package com.mattoid.scheduled.entity;

import lombok.Data;

import java.util.Map;

@Data
public class WebhookConfig {

    private String configName;

    /**
     * 请求 URL
     */
    private String url;

    /**
     * HTTP 方法：GET / POST / PUT
     */
    private String method;

    /**
     * 请求头
     */
    private Map<String, String> headers;

    /**
     * 请求体模板，支持占位符
     */
    private String bodyTemplate;

    /**
     * 超时秒数
     */
    private Integer timeoutSeconds;

    private Integer status;
}
