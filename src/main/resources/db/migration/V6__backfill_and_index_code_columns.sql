-- V6: 补齐编码字段并为已存在的通知配置回填默认编码（幂等，兼容 V5 已应用的环境）

-- ===================== notification_config.config_code =====================
SET @col_exists = (SELECT COUNT(1) FROM information_schema.columns
                   WHERE table_schema = DATABASE()
                     AND table_name = 'notification_config'
                     AND column_name = 'config_code');
SET @sql = IF(@col_exists = 0,
              'ALTER TABLE notification_config ADD COLUMN config_code VARCHAR(128) NULL COMMENT "配置编码"',
              'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 为已有空编码的通知配置生成默认编码
UPDATE notification_config SET config_code = CONCAT('CONFIG_', id)
 WHERE config_code IS NULL OR config_code = '';

-- 唯一索引（幂存：先删后建）
SET @idx_exists = (SELECT COUNT(1) FROM information_schema.statistics
                   WHERE table_schema = DATABASE()
                     AND table_name = 'notification_config'
                     AND index_name = 'uk_notification_config_code');
SET @sql = IF(@idx_exists = 0,
              'CREATE UNIQUE INDEX uk_notification_config_code ON notification_config(config_code)',
              'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ===================== notification_rule.config_code / task_code =====================
SET @col_exists = (SELECT COUNT(1) FROM information_schema.columns
                   WHERE table_schema = DATABASE()
                     AND table_name = 'notification_rule'
                     AND column_name = 'config_code');
SET @sql = IF(@col_exists = 0,
              'ALTER TABLE notification_rule ADD COLUMN config_code VARCHAR(128) NULL COMMENT "关联通知配置编码"',
              'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(1) FROM information_schema.columns
                   WHERE table_schema = DATABASE()
                     AND table_name = 'notification_rule'
                     AND column_name = 'task_code');
SET @sql = IF(@col_exists = 0,
              'ALTER TABLE notification_rule ADD COLUMN task_code VARCHAR(128) NULL COMMENT "关联任务编码"',
              'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ===================== task_sql_config.template_code / group_code =====================
SET @col_exists = (SELECT COUNT(1) FROM information_schema.columns
                   WHERE table_schema = DATABASE()
                     AND table_name = 'task_sql_config'
                     AND column_name = 'template_code');
SET @sql = IF(@col_exists = 0,
              'ALTER TABLE task_sql_config ADD COLUMN template_code VARCHAR(128) NULL COMMENT "报表模板编码"',
              'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(1) FROM information_schema.columns
                   WHERE table_schema = DATABASE()
                     AND table_name = 'task_sql_config'
                     AND column_name = 'group_code');
SET @sql = IF(@col_exists = 0,
              'ALTER TABLE task_sql_config ADD COLUMN group_code VARCHAR(128) NULL COMMENT "SQL 分组编码"',
              'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ===================== task_sql_relation.task_code / sql_code =====================
SET @col_exists = (SELECT COUNT(1) FROM information_schema.columns
                   WHERE table_schema = DATABASE()
                     AND table_name = 'task_sql_relation'
                     AND column_name = 'task_code');
SET @sql = IF(@col_exists = 0,
              'ALTER TABLE task_sql_relation ADD COLUMN task_code VARCHAR(128) NULL COMMENT "任务编码"',
              'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(1) FROM information_schema.columns
                   WHERE table_schema = DATABASE()
                     AND table_name = 'task_sql_relation'
                     AND column_name = 'sql_code');
SET @sql = IF(@col_exists = 0,
              'ALTER TABLE task_sql_relation ADD COLUMN sql_code VARCHAR(128) NULL COMMENT "SQL 编码"',
              'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
