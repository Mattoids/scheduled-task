ALTER TABLE task_web_crawl_config
    ADD COLUMN ssh_auth_type VARCHAR(20) DEFAULT 'PASSWORD'
        COMMENT 'SSH 认证方式：PASSWORD / KEY';
