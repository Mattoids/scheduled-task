-- SQL 配置增加自定义参数字段
SET @col_exists = (SELECT COUNT(1) FROM information_schema.columns
                   WHERE table_schema = DATABASE()
                     AND table_name = 'task_sql_config'
                     AND column_name = 'custom_params');
SET @sql = IF(@col_exists = 0,
              'ALTER TABLE task_sql_config ADD COLUMN custom_params TEXT NULL COMMENT "自定义参数 JSON，key 对应 SQL 中的 ${xxx} 占位符"',
              'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
