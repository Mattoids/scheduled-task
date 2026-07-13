package com.mattoid.scheduled.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 企业微信可信 IP 同步日志。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wecom_ip_sync_log")
public class WeComIpSyncLog extends BaseEntity {

    /** 触发方式：手动 */
    public static final String TRIGGER_MANUAL = "MANUAL";
    /** 触发方式：定时自动 */
    public static final String TRIGGER_AUTO = "AUTO";

    /** 同步状态：成功（实际发生了 IP 替换） */
    public static final String STATUS_SUCCESS = "SUCCESS";
    /** 同步状态：失败 */
    public static final String STATUS_FAIL = "FAIL";

    /** 失败原因：管理后台 Cookie 未配置 */
    public static final String FAIL_COOKIE_MISSING = "COOKIE_MISSING";
    /** 失败原因：管理后台 Cookie 失效（被重定向到登录页） */
    public static final String FAIL_COOKIE_INVALID = "COOKIE_INVALID";
    /** 失败原因：公网 IP 检测/解析失败 */
    public static final String FAIL_IP_DETECT = "IP_DETECT_FAIL";
    /** 失败原因：通知配置不存在 */
    public static final String FAIL_CONFIG_NOT_FOUND = "CONFIG_NOT_FOUND";
    /** 失败原因：同步过程异常 */
    public static final String FAIL_SYNC = "SYNC_FAIL";

    private Long configId;

    private String configName;

    /** MANUAL / AUTO */
    private String triggerType;

    /** SUCCESS / FAIL */
    private String status;

    /** 检测到的公网 IP（多 WAN 时为分号分隔的多个 IP） */
    private String detectedIp;

    /** 实际使用的 IP 检测源 URL */
    private String ipSource;

    /** 同步前可信 IP 列表 */
    private String oldIps;

    /** 同步后可信 IP 列表 */
    private String newIps;

    /** 失败原因，见 FAIL_* 常量 */
    private String failReason;

    private String message;

    private Long durationMs;

    private LocalDateTime startTime;

    private LocalDateTime endTime;
}
