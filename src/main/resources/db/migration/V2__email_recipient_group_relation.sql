-- 收件人与收件组多对多关系
CREATE TABLE IF NOT EXISTS email_recipient_group_relation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    recipient_id BIGINT NOT NULL,
    group_id BIGINT NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_recipient_group (recipient_id, group_id)
);

-- 迁移历史数据：将 email_recipient.group_id 写入关联表（幂等）
INSERT IGNORE INTO email_recipient_group_relation (recipient_id, group_id)
SELECT id, group_id FROM email_recipient WHERE group_id IS NOT NULL;
