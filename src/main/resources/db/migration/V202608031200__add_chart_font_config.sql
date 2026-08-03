ALTER TABLE task_sql_config ADD COLUMN chart_font_family VARCHAR(64) NULL COMMENT '图表字体';
ALTER TABLE task_sql_config ADD COLUMN chart_font_size INT NULL COMMENT '图表字号';

ALTER TABLE task_web_crawl_config ADD COLUMN chart_font_family VARCHAR(64) NULL;
ALTER TABLE task_web_crawl_config ADD COLUMN chart_font_size INT NULL;
