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
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

-- 角色
CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_code VARCHAR(64) NOT NULL UNIQUE,
    role_name VARCHAR(64) NOT NULL,
    description VARCHAR(255),
    status TINYINT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
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
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

-- 用户角色关联
CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_user_role (user_id, role_id)
);

-- 角色权限关联
CREATE TABLE IF NOT EXISTS sys_role_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
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
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

-- 发件邮箱配置
CREATE TABLE IF NOT EXISTS email_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_name VARCHAR(128) NOT NULL,
    smtp_host VARCHAR(128) NOT NULL,
    smtp_port INT NOT NULL,
    username VARCHAR(128) NOT NULL,
    password VARCHAR(256),
    from_address VARCHAR(128) NOT NULL,
    from_name VARCHAR(128),
    auth TINYINT DEFAULT 1,
    starttls TINYINT DEFAULT 1,
    `ssl` TINYINT DEFAULT 0,
    status TINYINT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

-- 收件人分组
CREATE TABLE IF NOT EXISTS email_recipient_group (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_name VARCHAR(128) NOT NULL,
    group_code VARCHAR(128) NOT NULL UNIQUE,
    description VARCHAR(512),
    status TINYINT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

-- 收件人
CREATE TABLE IF NOT EXISTS email_recipient (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recipient_name VARCHAR(128),
    email VARCHAR(128) NOT NULL,
    group_id BIGINT,
    status TINYINT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
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
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

-- 定时任务配置
CREATE TABLE IF NOT EXISTS task_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_name VARCHAR(128) NOT NULL,
    task_code VARCHAR(128) NOT NULL UNIQUE,
    description VARCHAR(512),
    trigger_type VARCHAR(32) NOT NULL COMMENT 'ONCE / CRON',
    trigger_config VARCHAR(256) NOT NULL,
    datasource_id BIGINT NOT NULL,
    sql_content TEXT NOT NULL,
    email_config_id BIGINT NOT NULL,
    recipient_ids VARCHAR(1024) COMMENT '逗号分隔的收件人 ID',
    template_id BIGINT,
    status VARCHAR(32) DEFAULT 'ENABLE' COMMENT 'ENABLE / DISABLE',
    file_name_pattern VARCHAR(256),
    email_subject VARCHAR(256),
    email_body TEXT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
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
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);
