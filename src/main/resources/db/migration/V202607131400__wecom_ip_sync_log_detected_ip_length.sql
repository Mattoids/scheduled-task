-- 多 WAN 场景 detected_ip 需存放多个分号分隔的 IP，放宽长度限制
ALTER TABLE wecom_ip_sync_log
    MODIFY COLUMN detected_ip VARCHAR(255) NULL COMMENT '检测到的公网 IP（多 WAN 时为分号分隔的多个 IP）';
