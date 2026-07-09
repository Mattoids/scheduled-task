ALTER TABLE task_web_crawl_config
    ADD COLUMN ssh_hops TEXT COMMENT 'SSH 多跳链路配置（跳板机/代理机），按从服务侧到请求侧排序的 JSON 数组';
