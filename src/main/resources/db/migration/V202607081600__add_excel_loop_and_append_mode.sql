ALTER TABLE task_sql_config
    ADD COLUMN excel_loop_enabled TINYINT DEFAULT 0 COMMENT '是否启用 Excel 循环生成 sheet：1 启用，0 禁用' AFTER excel_sheet_name,
    ADD COLUMN excel_loop_config TEXT NULL COMMENT 'Excel 循环生成配置 JSON' AFTER excel_loop_enabled,
    ADD COLUMN excel_append_mode TINYINT DEFAULT 0 COMMENT '是否启用 Excel 追加模式：1 启用，0 禁用' AFTER excel_loop_config,
    ADD COLUMN excel_base_file_path VARCHAR(500) NULL COMMENT 'Excel 基础文件路径（追加模式），支持占位符' AFTER excel_append_mode;

