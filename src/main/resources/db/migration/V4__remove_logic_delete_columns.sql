-- 从逻辑删除迁移到物理删除：清理已删除记录、删除 deleted 字段、恢复唯一索引
-- 使用存储过程兼容 MySQL 5.7
DELIMITER $$

DROP PROCEDURE IF EXISTS scheduled_task_remove_logic_delete_columns$$

CREATE PROCEDURE scheduled_task_remove_logic_delete_columns()
BEGIN
    DECLARE done INT DEFAULT 0;
    DECLARE tbl VARCHAR(64);
    DECLARE tables_cursor CURSOR FOR
        SELECT table_name FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND column_name = 'deleted'
          AND table_name IN (
              'sys_user', 'sys_role', 'sys_permission', 'sys_user_role', 'sys_role_permission',
              'datasource_config', 'email_config', 'email_recipient_group', 'email_recipient',
              'report_template', 'task_config', 'task_log', 'task_sql_config', 'task_sql_relation'
          );
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET done = 1;

    -- 1. 清理 task_sql_relation 中已逻辑删除的记录，避免恢复唯一索引时报重复
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'task_sql_relation'
          AND column_name = 'deleted'
    ) THEN
        DELETE FROM task_sql_relation WHERE deleted = 1;
    END IF;

    -- 2. 遍历所有包含 deleted 字段的业务表并删除该字段
    OPEN tables_cursor;
    read_loop: LOOP
        FETCH tables_cursor INTO tbl;
        IF done THEN
            LEAVE read_loop;
        END IF;
        SET @drop_sql = CONCAT('ALTER TABLE ', tbl, ' DROP COLUMN deleted');
        PREPARE stmt FROM @drop_sql;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END LOOP;
    CLOSE tables_cursor;

    -- 3. 恢复 task_sql_relation 的物理唯一索引
    IF EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'task_sql_relation'
          AND index_name = 'uk_task_sql'
    ) THEN
        ALTER TABLE task_sql_relation DROP INDEX uk_task_sql;
    END IF;
    ALTER TABLE task_sql_relation ADD UNIQUE KEY uk_task_sql (task_id, sql_id);
END$$

DELIMITER ;

CALL scheduled_task_remove_logic_delete_columns();
DROP PROCEDURE IF EXISTS scheduled_task_remove_logic_delete_columns;
