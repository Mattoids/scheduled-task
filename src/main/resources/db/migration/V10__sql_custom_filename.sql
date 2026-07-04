-- SQL 配置支持自定义文件名
ALTER TABLE task_sql_config ADD COLUMN file_name_pattern VARCHAR(256) NULL AFTER file_suffix;

-- 清理已删除分组留下的孤儿关联
UPDATE task_sql_config tsc
LEFT JOIN task_sql_group tsg ON tsc.group_id = tsg.id
SET tsc.group_id = NULL
WHERE tsg.id IS NULL;
