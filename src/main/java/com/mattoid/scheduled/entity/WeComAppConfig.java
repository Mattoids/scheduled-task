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

    /** 是否启用自动同步可信 IP */
    private Boolean autoSyncIp;

    /** 关联的企业微信管理账户 ID（用于 IP 同步时获取 Cookie） */
    private Long adminAccountId;

    /** 企业微信管理后台 Cookie（AES 加密存储，向后兼容；优先使用 adminAccountId） */
    private String adminCookie;

    /** IP 同步间隔（分钟），默认 10 */
    private Integer syncIntervalMinutes;

    /** IP 检测源 URL，支持预设站点或自定义地址 */
    private String ipDetectionUrl;

    /**
     * 应用管理页 URL（可选）。
     * 新版企业微信后台的应用详情 URL 使用内部注册 ID 而非 agentId，
     * 形如 https://work.weixin.qq.com/wework_admin/frame#apps/modApiApp/5629502132772163。
     * 留空则按 agentId 拼接（兼容旧版后台）。
     */
    private String appManageUrl;
}
