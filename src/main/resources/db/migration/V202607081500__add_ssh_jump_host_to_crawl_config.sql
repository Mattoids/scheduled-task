ALTER TABLE task_web_crawl_config
    ADD COLUMN ssh_jump_host_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否使用跳板机' AFTER ssh_local_port,
    ADD COLUMN ssh_jump_host_host VARCHAR(255) NULL COMMENT '跳板机地址' AFTER ssh_jump_host_enabled,
    ADD COLUMN ssh_jump_host_port INT NULL COMMENT '跳板机端口' AFTER ssh_jump_host_host,
    ADD COLUMN ssh_jump_host_username VARCHAR(255) NULL COMMENT '跳板机用户名' AFTER ssh_jump_host_port,
    ADD COLUMN ssh_jump_host_password VARCHAR(2000) NULL COMMENT '跳板机密码（加密）' AFTER ssh_jump_host_username,
    ADD COLUMN ssh_jump_host_private_key TEXT NULL COMMENT '跳板机私钥（加密）' AFTER ssh_jump_host_password,
    ADD COLUMN ssh_jump_host_passphrase VARCHAR(2000) NULL COMMENT '跳板机私钥口令（加密）' AFTER ssh_jump_host_private_key,
    ADD COLUMN ssh_jump_host_auth_type VARCHAR(20) NULL COMMENT '跳板机认证方式 PASSWORD/KEY' AFTER ssh_jump_host_passphrase;
