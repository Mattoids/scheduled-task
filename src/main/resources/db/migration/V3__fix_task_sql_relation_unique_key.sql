-- 修复逻辑删除导致无法重新添加同一条任务-SQL 关联的问题
ALTER TABLE task_sql_relation DROP INDEX uk_task_sql;
ALTER TABLE task_sql_relation ADD UNIQUE KEY uk_task_sql (task_id, sql_id, deleted);
