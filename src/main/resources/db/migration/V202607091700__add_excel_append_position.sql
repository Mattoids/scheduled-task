ALTER TABLE task_sql_config
    ADD COLUMN excel_append_position INT DEFAULT NULL COMMENT 'Excel 追加模式新 sheet 插入位置，从 0 开始，null 或负数表示追加到末尾' AFTER excel_append_update_same_sheet;
