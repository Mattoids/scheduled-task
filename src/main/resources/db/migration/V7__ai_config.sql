-- AI 配置表，支持多厂商智能助理
CREATE TABLE IF NOT EXISTS ai_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_name VARCHAR(128) NOT NULL COMMENT '配置名称',
    provider VARCHAR(64) NOT NULL COMMENT '厂商：OPENAI / ANTHROPIC / AZURE_OPENAI / OLLAMA / CUSTOM',
    api_key VARCHAR(512) COMMENT 'API Key',
    base_url VARCHAR(256) COMMENT '基础 URL，支持自定义代理或私有部署',
    model VARCHAR(128) NOT NULL COMMENT '模型名称',
    temperature DECIMAL(3, 2) DEFAULT 0.7 COMMENT '温度参数',
    max_tokens INT DEFAULT 2048 COMMENT '单次最大 token 数',
    timeout_seconds INT DEFAULT 60 COMMENT '请求超时秒数',
    is_default TINYINT DEFAULT 0 COMMENT '是否为默认配置：1 是，0 否',
    status TINYINT DEFAULT 1 COMMENT '状态：1 启用，0 禁用',
    remark VARCHAR(512) COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ai_config_name (config_name)
);

-- 初始化一条默认禁用示例（避免空表，用户可在页面配置真实 key 后启用）
INSERT INTO ai_config (config_name, provider, model, status, is_default, remark)
VALUES ('默认 OpenAI 配置', 'OPENAI', 'gpt-4o-mini', 0, 1, '请在页面中配置真实 API Key 后启用')
ON DUPLICATE KEY UPDATE id = id;
