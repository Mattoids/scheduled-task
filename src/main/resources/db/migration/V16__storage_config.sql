-- 存储配置表
CREATE TABLE IF NOT EXISTS storage_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_name VARCHAR(128) NOT NULL COMMENT '配置名称',
    storage_type VARCHAR(32) NOT NULL COMMENT '存储类型：LOCAL / OSS / S3 / WEBDAV',
    config_json TEXT COMMENT '配置 JSON',
    status TINYINT DEFAULT 1 COMMENT '状态：1 启用，0 禁用',
    is_default TINYINT DEFAULT 0 COMMENT '是否默认：1 默认，0 非默认',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 通知规则关联存储配置
ALTER TABLE notification_rule ADD COLUMN storage_config_id BIGINT NULL COMMENT '存储配置 ID，为空时直接发送文件，不为空时上传到存储系统后发链接';
