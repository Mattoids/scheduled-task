-- 企业微信可信 IP 同步日志：记录每次 IP 同步的时间、检测到的公网 IP、同步状态与失败原因，便于排查 Cookie 失效等问题
CREATE TABLE IF NOT EXISTS wecom_ip_sync_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_id BIGINT NULL COMMENT '通知配置 ID（notification_config.id）',
    config_name VARCHAR(128) NULL COMMENT '通知配置名称（冗余，便于展示）',
    trigger_type VARCHAR(16) NOT NULL COMMENT '触发方式：MANUAL 手动 / AUTO 定时',
    status VARCHAR(16) NOT NULL COMMENT '同步状态：SUCCESS 成功（实际替换） / FAIL 失败',
    detected_ip VARCHAR(64) NULL COMMENT '检测到的公网 IP',
    ip_source VARCHAR(512) NULL COMMENT '实际使用的 IP 检测源 URL',
    old_ips VARCHAR(1024) NULL COMMENT '同步前可信 IP 列表',
    new_ips VARCHAR(1024) NULL COMMENT '同步后可信 IP 列表',
    fail_reason VARCHAR(32) NULL COMMENT '失败原因：COOKIE_MISSING 未配置Cookie / COOKIE_INVALID Cookie失效 / IP_DETECT_FAIL IP检测解析失败 / CONFIG_NOT_FOUND 配置不存在 / SYNC_FAIL 同步失败',
    message VARCHAR(1024) NULL COMMENT '结果描述',
    duration_ms BIGINT NULL COMMENT '耗时（毫秒）',
    start_time DATETIME NULL COMMENT '开始时间',
    end_time DATETIME NULL COMMENT '结束时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_config_create (config_id, create_time),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='企业微信可信 IP 同步日志';
