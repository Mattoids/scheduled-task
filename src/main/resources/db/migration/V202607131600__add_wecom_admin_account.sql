-- 企业微信管理账户：用于「企业应用管理」菜单，支持多个企业微信管理后台 Cookie 的管理、保活与免登录跳转
CREATE TABLE IF NOT EXISTS wecom_admin_account (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_name VARCHAR(128) NOT NULL COMMENT '账户自定义名称',
    admin_cookie TEXT NULL COMMENT '企业微信管理后台 Cookie（AES 加密存储）',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1 启用 / 0 停用（停用后不执行 Cookie 保活）',
    keep_alive_enabled TINYINT NOT NULL DEFAULT 1 COMMENT '是否启用 Cookie 保活定时任务',
    last_keep_alive_time DATETIME NULL COMMENT '最近一次保活访问时间',
    last_keep_alive_result VARCHAR(512) NULL COMMENT '最近一次保活结果描述',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='企业微信管理账户';
