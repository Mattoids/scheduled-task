-- 将 AI 优化通知从任务配置迁移到通知规则
ALTER TABLE notification_rule ADD COLUMN ai_optimize_notify TINYINT DEFAULT 0 COMMENT '是否启用 AI 优化通知内容：1 启用，0 禁用';
ALTER TABLE notification_rule ADD COLUMN ai_config_id BIGINT NULL COMMENT 'AI 配置 ID，为空时使用默认配置';

ALTER TABLE task_config DROP COLUMN ai_optimize_notify;
ALTER TABLE task_config DROP COLUMN ai_config_id;
