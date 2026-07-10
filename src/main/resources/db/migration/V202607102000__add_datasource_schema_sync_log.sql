-- 数据源表结构同步日志：记录每次同步的状态、耗时、表数、生成文档与错误信息，便于查询同步结果
CREATE TABLE IF NOT EXISTS datasource_schema_sync_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    datasource_id BIGINT NOT NULL COMMENT '数据源 ID',
    datasource_name VARCHAR(128) NULL COMMENT '数据源名称（冗余，便于展示）',
    status VARCHAR(16) NOT NULL COMMENT '状态：RUNNING/SUCCESS/FAIL',
    table_count INT NULL COMMENT '同步到的表数量',
    doc_id BIGINT NULL COMMENT '生成的数据字典文档 ID（ai_knowledge_doc.id）',
    doc_title VARCHAR(255) NULL COMMENT '生成的数据字典标题',
    error_message TEXT NULL COMMENT '失败原因',
    duration_ms BIGINT NULL COMMENT '耗时（毫秒）',
    start_time DATETIME NULL COMMENT '开始时间',
    end_time DATETIME NULL COMMENT '结束时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_datasource_create (datasource_id, create_time),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据源表结构同步日志';
