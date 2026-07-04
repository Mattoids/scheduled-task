ALTER TABLE notification_rule ADD COLUMN task_id BIGINT NULL;

CREATE INDEX idx_notification_rule_task_id ON notification_rule(task_id);
