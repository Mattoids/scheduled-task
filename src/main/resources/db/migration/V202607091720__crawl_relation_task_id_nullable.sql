-- 任务与网页爬取配置的关联改为基于编码（task_code / crawl_code），不再依赖 task_id
ALTER TABLE task_web_crawl_relation
    MODIFY COLUMN task_id BIGINT NULL COMMENT '任务 ID（已废弃，使用 task_code）',
    MODIFY COLUMN crawl_id BIGINT NULL COMMENT '爬取配置 ID（已废弃，使用 crawl_code）',
    MODIFY COLUMN task_code VARCHAR(255) NOT NULL COMMENT '任务编码',
    MODIFY COLUMN crawl_code VARCHAR(255) NOT NULL COMMENT '爬取配置编码',
    ADD UNIQUE KEY uk_task_crawl_relation_code (task_code, crawl_code);
