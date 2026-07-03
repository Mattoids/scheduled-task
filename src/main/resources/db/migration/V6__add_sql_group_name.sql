-- SQL 配置增加分组名称，便于任务选择时按分组展示
ALTER TABLE task_sql_config ADD COLUMN group_name VARCHAR(128) NULL COMMENT 'SQL 分组名称' AFTER file_name_pattern;

CREATE INDEX idx_task_sql_group_name ON task_sql_config(group_name);
