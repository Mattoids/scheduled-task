CREATE DATABASE IF NOT EXISTS scheduled_task DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE scheduled_task;

-- 系统用户
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password VARCHAR(128) NOT NULL,
    nickname VARCHAR(64),
    email VARCHAR(128),
    phone VARCHAR(32),
    status TINYINT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 角色
CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_code VARCHAR(64) NOT NULL UNIQUE,
    role_name VARCHAR(64) NOT NULL,
    description VARCHAR(255),
    status TINYINT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 权限
CREATE TABLE IF NOT EXISTS sys_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    permission_code VARCHAR(128) NOT NULL UNIQUE,
    permission_name VARCHAR(128) NOT NULL,
    resource_type VARCHAR(32),
    parent_id BIGINT DEFAULT 0,
    sort_order INT DEFAULT 0,
    path VARCHAR(255),
    status TINYINT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 用户角色关联
CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_role (user_id, role_id)
);

-- 角色权限关联
CREATE TABLE IF NOT EXISTS sys_role_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_role_permission (role_id, permission_id)
);

-- 数据源配置
CREATE TABLE IF NOT EXISTS datasource_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    db_type VARCHAR(32) NOT NULL,
    host VARCHAR(128) NOT NULL,
    port INT NOT NULL,
    database_name VARCHAR(128) NOT NULL,
    username VARCHAR(128) NOT NULL,
    password VARCHAR(256),
    driver_class VARCHAR(256),
    jdbc_url_params VARCHAR(512),
    ssh_enabled TINYINT DEFAULT 0,
    ssh_host VARCHAR(128),
    ssh_port INT DEFAULT 22,
    ssh_username VARCHAR(128),
    ssh_password VARCHAR(256),
    ssh_private_key TEXT,
    ssh_passphrase VARCHAR(256),
    ssh_local_port INT,
    remark VARCHAR(512),
    status TINYINT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 通知配置
CREATE TABLE IF NOT EXISTS notification_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_name VARCHAR(255) NOT NULL COMMENT '配置名称',
    config_type VARCHAR(32) NOT NULL COMMENT '配置类型：EMAIL / WECOM_APP / WECOM_BOT / WECOM_INTELLIGENT_BOT',
    config_json TEXT NOT NULL COMMENT '类型相关 JSON 配置',
    status TINYINT DEFAULT 1 COMMENT '状态：1 启用，0 禁用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 收件人分组
CREATE TABLE IF NOT EXISTS email_recipient_group (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_name VARCHAR(128) NOT NULL,
    group_code VARCHAR(128) NOT NULL UNIQUE,
    description VARCHAR(512),
    status TINYINT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 收件人
CREATE TABLE IF NOT EXISTS email_recipient (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recipient_name VARCHAR(128),
    email VARCHAR(128) NOT NULL,
    group_id BIGINT,
    status TINYINT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 报表模板
CREATE TABLE IF NOT EXISTS report_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_name VARCHAR(128) NOT NULL,
    template_code VARCHAR(128) NOT NULL UNIQUE,
    template_type VARCHAR(32) NOT NULL,
    file_path VARCHAR(512) NOT NULL,
    file_name VARCHAR(256),
    description VARCHAR(512),
    status TINYINT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 定时任务配置
CREATE TABLE IF NOT EXISTS task_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_name VARCHAR(128) NOT NULL,
    task_code VARCHAR(128) NOT NULL UNIQUE,
    description VARCHAR(512),
    trigger_type VARCHAR(32) NOT NULL COMMENT 'ONCE / CRON',
    trigger_config VARCHAR(256) NOT NULL,
    status VARCHAR(32) DEFAULT 'ENABLE' COMMENT 'ENABLE / DISABLE',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 任务执行日志
CREATE TABLE IF NOT EXISTS task_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    trigger_mode VARCHAR(32) COMMENT 'MANUAL / AUTO',
    start_time DATETIME,
    end_time DATETIME,
    status VARCHAR(32) COMMENT 'RUNNING / SUCCESS / FAILED',
    result_message TEXT,
    error_message TEXT,
    file_path VARCHAR(512),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- SQL 分组
CREATE TABLE IF NOT EXISTS task_sql_group (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_name VARCHAR(128) NOT NULL,
    group_code VARCHAR(128) NOT NULL UNIQUE,
    file_name_pattern VARCHAR(256),
    description VARCHAR(512),
    status TINYINT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- SQL 语句模块
CREATE TABLE IF NOT EXISTS task_sql_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sql_name VARCHAR(128) NOT NULL,
    sql_code VARCHAR(128) NOT NULL UNIQUE,
    datasource_id BIGINT NOT NULL,
    sql_content TEXT NOT NULL,
    template_id BIGINT,
    group_id BIGINT,
    output_format VARCHAR(32) COMMENT 'CSV / EXCEL / WORD / PPT / TXT',
    file_suffix VARCHAR(32),
    file_name_pattern VARCHAR(256),
    description VARCHAR(512),
    status TINYINT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 任务与 SQL 关联
CREATE TABLE IF NOT EXISTS task_sql_relation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    sql_id BIGINT NOT NULL,
    sort_order INT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_task_sql (task_id, sql_id)
);

-- 全局通知规则
CREATE TABLE IF NOT EXISTS notification_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type VARCHAR(32) NOT NULL COMMENT 'TASK_SUCCESS / TASK_FAILURE / TASK_COMPLETED',
    channel VARCHAR(32) NOT NULL COMMENT 'EMAIL / WECOM_APP / WECOM_BOT / WECOM_INTELLIGENT_BOT',
    config_id BIGINT COMMENT '对应 notification_config 的 id',
    recipient_ids VARCHAR(1024) COMMENT 'EMAIL 渠道：收件人 id，逗号分隔',
    recipient_group_ids VARCHAR(1024) COMMENT 'EMAIL 渠道：收件人群组 id，逗号分隔',
    wecom_to_user VARCHAR(500) COMMENT 'WeCom 渠道：接收人 / 被提及用户',
    subject VARCHAR(256) COMMENT '邮件主题模板',
    body TEXT COMMENT '邮件正文模板',
    content TEXT COMMENT 'WeCom 文本模板',
    ai_optimize_notify TINYINT DEFAULT 0 COMMENT '是否启用 AI 优化通知内容：1 启用，0 禁用',
    ai_config_id BIGINT NULL COMMENT 'AI 配置 ID，为空时使用默认配置',
    enabled TINYINT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- AI 配置表
CREATE TABLE IF NOT EXISTS ai_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_name VARCHAR(128) NOT NULL COMMENT '配置名称',
    provider VARCHAR(64) NOT NULL COMMENT '厂商：OPENAI / ANTHROPIC / AZURE_OPENAI / OLLAMA / CUSTOM',
    api_key VARCHAR(512) COMMENT 'API Key',
    base_url VARCHAR(256) COMMENT '基础 URL',
    model VARCHAR(128) NOT NULL COMMENT '模型名称',
    temperature DECIMAL(3, 2) DEFAULT 0.7 COMMENT '温度参数',
    max_tokens INT DEFAULT 2048 COMMENT '单次最大 token 数',
    timeout_seconds INT DEFAULT 60 COMMENT '请求超时秒数',
    is_default TINYINT DEFAULT 0 COMMENT '是否为默认配置：1 是，0 否',
    status TINYINT DEFAULT 1 COMMENT '状态：1 启用，0 禁用',
    remark VARCHAR(512) COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ai_config_name (config_name)
);

-- 初始化数据在 InitDataRunner 中完成
