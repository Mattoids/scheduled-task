ALTER TABLE wecom_app_config
    ADD COLUMN proxy_url VARCHAR(512) NULL COMMENT '企业微信 API 代理地址，为空时直接访问 qyapi.weixin.qq.com';

CREATE TABLE IF NOT EXISTS wecom_intelligent_bot_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_name VARCHAR(255) NOT NULL COMMENT '配置名称',
    webhook_key VARCHAR(255) NOT NULL COMMENT '智能机器人 Webhook Key',
    status TINYINT DEFAULT 1 COMMENT '状态：1 启用，0 禁用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
