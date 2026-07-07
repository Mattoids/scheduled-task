-- V8: task_sql_relation 改为基于编码关联，id 字段不再必填

-- 删除可能的重复编码关联，保留 id 最小的一条
DELETE s1 FROM task_sql_relation s1
INNER JOIN task_sql_relation s2
    ON s1.task_code = s2.task_code
    AND s1.sql_code = s2.sql_code
    AND s1.id > s2.id
WHERE s1.task_code IS NOT NULL
  AND s1.task_code != ''
  AND s1.sql_code IS NOT NULL
  AND s1.sql_code != '';

-- id 字段改为可空
ALTER TABLE task_sql_relation MODIFY COLUMN task_id BIGINT NULL COMMENT '任务 ID（已废弃，使用 task_code）';
ALTER TABLE task_sql_relation MODIFY COLUMN sql_id BIGINT NULL COMMENT 'SQL ID（已废弃，使用 sql_code）';

-- 基于编码查询的索引
CREATE INDEX idx_task_sql_relation_task_code ON task_sql_relation(task_code);
CREATE UNIQUE INDEX uk_task_sql_relation_code ON task_sql_relation(task_code, sql_code);
