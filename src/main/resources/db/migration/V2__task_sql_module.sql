-- SQL 语句模块
CREATE TABLE IF NOT EXISTS task_sql_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sql_name VARCHAR(128) NOT NULL,
    sql_code VARCHAR(128) NOT NULL UNIQUE,
    datasource_id BIGINT NOT NULL,
    sql_content TEXT NOT NULL,
    template_id BIGINT,
    output_format VARCHAR(32) COMMENT 'CSV / EXCEL / WORD / PPT / TXT',
    file_suffix VARCHAR(32),
    file_name_pattern VARCHAR(256),
    description VARCHAR(512),
    status TINYINT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
);

-- 任务与 SQL 关联
CREATE TABLE IF NOT EXISTS task_sql_relation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL,
    sql_id BIGINT NOT NULL,
    sort_order INT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_task_sql (task_id, sql_id)
);

-- 改造定时任务配置：移除旧 SQL/数据源/模板字段，新增收件人群组字段
-- 使用存储过程实现 MySQL 5.7 下的幂等变更
DELIMITER $$

DROP PROCEDURE IF EXISTS scheduled_task_sql_module_alter$$

CREATE PROCEDURE scheduled_task_sql_module_alter()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'task_config'
          AND column_name = 'recipient_group_ids'
    ) THEN
        ALTER TABLE task_config ADD COLUMN recipient_group_ids VARCHAR(1024) COMMENT '逗号分隔的收件人群组 ID';
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'task_config'
          AND column_name = 'datasource_id'
    ) THEN
        ALTER TABLE task_config DROP COLUMN datasource_id;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'task_config'
          AND column_name = 'sql_content'
    ) THEN
        ALTER TABLE task_config DROP COLUMN sql_content;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'task_config'
          AND column_name = 'template_id'
    ) THEN
        ALTER TABLE task_config DROP COLUMN template_id;
    END IF;
END$$

DELIMITER ;

CALL scheduled_task_sql_module_alter();
DROP PROCEDURE IF EXISTS scheduled_task_sql_module_alter;
