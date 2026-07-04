-- SQL 分组主表
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

-- SQL 配置增加分组外键（已存在的列会被 Runner 忽略）
ALTER TABLE task_sql_config ADD COLUMN group_id BIGINT NULL AFTER template_id;
CREATE INDEX idx_task_sql_group_id ON task_sql_config(group_id);

-- 迁移现有 SQL 分组与文件名到主表
-- 1. 根据已有 group_name + file_name_pattern 创建分组（group_code 使用 MD5 保证唯一）
INSERT INTO task_sql_group (group_name, group_code, file_name_pattern, description, status)
SELECT DISTINCT
    COALESCE(group_name, '默认分组'),
    CONCAT('sql_grp_', MD5(CONCAT(COALESCE(group_name, '默认分组'), '|', COALESCE(file_name_pattern, '')))),
    file_name_pattern,
    NULL,
    1
FROM task_sql_config
WHERE group_name IS NOT NULL OR file_name_pattern IS NOT NULL;

-- 2. 为没有分组也没有文件名的数据创建默认分组
INSERT INTO task_sql_group (group_name, group_code, file_name_pattern, description, status)
SELECT '默认分组', 'sql_grp_default', NULL, NULL, 1
WHERE NOT EXISTS (SELECT 1 FROM task_sql_group WHERE group_code = 'sql_grp_default');

-- 3. 回写 group_id
UPDATE task_sql_config tsc
JOIN task_sql_group tsg
    ON tsg.group_name = COALESCE(tsc.group_name, '默认分组')
   AND COALESCE(tsg.file_name_pattern, '') = COALESCE(tsc.file_name_pattern, '')
SET tsc.group_id = tsg.id;

-- 4. 删除旧字段（已不存在的列会被 Runner 忽略）
ALTER TABLE task_sql_config DROP COLUMN group_name;
ALTER TABLE task_sql_config DROP COLUMN file_name_pattern;

-- 全局通知规则表
CREATE TABLE IF NOT EXISTS notification_rule (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_type VARCHAR(32) NOT NULL COMMENT 'TASK_SUCCESS / TASK_FAILURE / TASK_COMPLETED',
    channel VARCHAR(32) NOT NULL COMMENT 'EMAIL / WECOM_APP / WECOM_BOT',
    config_id BIGINT COMMENT '对应 email_config / wecom_app_config / wecom_bot_config 的 id',
    recipient_ids VARCHAR(1024) COMMENT 'EMAIL 渠道：收件人 id，逗号分隔',
    recipient_group_ids VARCHAR(1024) COMMENT 'EMAIL 渠道：收件人群组 id，逗号分隔',
    wecom_to_user VARCHAR(500) COMMENT 'WECOM_APP 渠道：接收人',
    subject VARCHAR(256) COMMENT '邮件主题模板',
    body TEXT COMMENT '邮件正文模板',
    content TEXT COMMENT 'WeCom 文本模板',
    enabled TINYINT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- 从现有任务通知配置迁移初始规则
INSERT INTO notification_rule (event_type, channel, config_id, recipient_ids, recipient_group_ids, subject, body, enabled)
SELECT DISTINCT
    'TASK_COMPLETED', 'EMAIL', email_config_id, recipient_ids, recipient_group_ids, email_subject, email_body, 1
FROM task_config
WHERE email_config_id IS NOT NULL;

INSERT INTO notification_rule (event_type, channel, config_id, wecom_to_user, enabled)
SELECT DISTINCT
    'TASK_COMPLETED', 'WECOM_APP', we_com_app_config_id, we_com_to_user, 1
FROM task_config
WHERE we_com_app_config_id IS NOT NULL;

INSERT INTO notification_rule (event_type, channel, config_id, enabled)
SELECT DISTINCT
    'TASK_COMPLETED', 'WECOM_BOT', we_com_bot_config_id, 1
FROM task_config
WHERE we_com_bot_config_id IS NOT NULL;

-- 删除任务级文件名与通知相关字段（已不存在的列会被 Runner 忽略）
ALTER TABLE task_config DROP COLUMN file_name_pattern;
ALTER TABLE task_config DROP COLUMN email_config_id;
ALTER TABLE task_config DROP COLUMN recipient_ids;
ALTER TABLE task_config DROP COLUMN recipient_group_ids;
ALTER TABLE task_config DROP COLUMN email_subject;
ALTER TABLE task_config DROP COLUMN email_body;
ALTER TABLE task_config DROP COLUMN we_com_app_config_id;
ALTER TABLE task_config DROP COLUMN we_com_bot_config_id;
ALTER TABLE task_config DROP COLUMN we_com_to_user;
