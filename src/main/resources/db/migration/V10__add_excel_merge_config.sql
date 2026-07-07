ALTER TABLE task_sql_config
    ADD COLUMN excel_merge_group VARCHAR(128) COMMENT 'Excel 合并组名，相同组名的 SQL 结果会合并到同一个 Excel 文件';

ALTER TABLE task_sql_config
    ADD COLUMN excel_sheet_name VARCHAR(128) COMMENT 'Excel 中 sheet 页名称；同一合并组内相同 sheet 名的 SQL 会追加到同一页';
