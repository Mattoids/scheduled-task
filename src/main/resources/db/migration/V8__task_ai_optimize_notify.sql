-- 任务配置增加 AI 通知优化开关
ALTER TABLE task_config ADD COLUMN ai_optimize_notify TINYINT DEFAULT 0 COMMENT '是否启用 AI 优化通知内容：1 启用，0 禁用';
