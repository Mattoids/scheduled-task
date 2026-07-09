ALTER TABLE task_web_crawl_config
    ADD COLUMN excel_sheet_name VARCHAR(500) COMMENT 'Excel 中 sheet 页名称，支持内置变量';
