ALTER TABLE task_sql_config
    ADD COLUMN chart_auto_merge TINYINT NOT NULL DEFAULT 1 COMMENT '图表分类过多时是否自动合并相邻数据：1 开启，0 关闭';

ALTER TABLE task_sql_config
    ADD COLUMN chart_label_rotation VARCHAR(16) DEFAULT 'AUTO' COMMENT 'X 轴标签旋转角度：AUTO / 0 / 45 / 90';
