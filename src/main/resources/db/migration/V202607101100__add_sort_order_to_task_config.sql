ALTER TABLE task_config
    ADD COLUMN sort_order INT DEFAULT 0 COMMENT '排序权重，值越大越靠前';
