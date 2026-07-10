-- AI 知识文档与会话表
CREATE TABLE IF NOT EXISTS ai_knowledge_doc (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    datasource_id BIGINT,
    doc_type VARCHAR(32) NOT NULL COMMENT '文档类型：SCHEMA 等',
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    status TINYINT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_datasource_id (datasource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 知识文档';

CREATE TABLE IF NOT EXISTS ai_conversation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL UNIQUE,
    user_id BIGINT,
    title VARCHAR(255),
    datasource_id BIGINT,
    doc_id BIGINT,
    messages JSON COMMENT '会话消息列表 JSON',
    status TINYINT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_session_id (session_id),
    INDEX idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 会话';
