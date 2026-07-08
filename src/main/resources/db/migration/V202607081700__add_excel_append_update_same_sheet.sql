ALTER TABLE task_sql_config
    ADD COLUMN excel_append_update_same_sheet TINYINT DEFAULT 0 COMMENT 'Excel 追加模式是否更新同名 sheet：1 更新，0 跳过' AFTER excel_base_file_path;
