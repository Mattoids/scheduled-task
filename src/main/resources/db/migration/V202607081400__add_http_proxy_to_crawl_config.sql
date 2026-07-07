ALTER TABLE task_web_crawl_config
    ADD COLUMN proxy_enabled TINYINT DEFAULT 0 COMMENT '是否启用 HTTP 代理',
    ADD COLUMN proxy_host VARCHAR(255) COMMENT 'HTTP 代理主机',
    ADD COLUMN proxy_port INT COMMENT 'HTTP 代理端口',
    ADD COLUMN proxy_username VARCHAR(255) COMMENT 'HTTP 代理用户名',
    ADD COLUMN proxy_password VARCHAR(500) COMMENT 'HTTP 代理密码，加密';
