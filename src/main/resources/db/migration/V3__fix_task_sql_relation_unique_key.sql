-- 删除逻辑删除字段，恢复唯一索引为物理唯一（兼容 MySQL 5.7）
DELIMITER $$

DROP PROCEDURE IF EXISTS scheduled_task_fix_task_sql_relation_uk$$

CREATE PROCEDURE scheduled_task_fix_task_sql_relation_uk()
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'task_sql_relation'
          AND index_name = 'uk_task_sql'
    ) THEN
        ALTER TABLE task_sql_relation DROP INDEX uk_task_sql;
    END IF;
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'task_sql_relation'
          AND column_name = 'deleted'
    ) THEN
        ALTER TABLE task_sql_relation DROP COLUMN deleted;
    END IF;
    ALTER TABLE task_sql_relation ADD UNIQUE KEY uk_task_sql (task_id, sql_id);
END$$

DELIMITER ;

CALL scheduled_task_fix_task_sql_relation_uk();
DROP PROCEDURE IF EXISTS scheduled_task_fix_task_sql_relation_uk;
