CREATE TABLE IF NOT EXISTS notification_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_name VARCHAR(255) NOT NULL COMMENT '配置名称',
    config_type VARCHAR(32) NOT NULL COMMENT '配置类型：EMAIL / WECOM_APP / WECOM_BOT / WECOM_INTELLIGENT_BOT',
    config_json TEXT NOT NULL COMMENT '类型相关 JSON 配置',
    status TINYINT DEFAULT 1 COMMENT '状态：1 启用，0 禁用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

DELETE FROM notification_config WHERE config_type = 'EMAIL';
INSERT INTO notification_config (config_name, config_type, config_json, status, create_time, update_time)
SELECT config_name,
       'EMAIL',
       JSON_OBJECT(
               'smtpHost', smtp_host,
               'smtpPort', smtp_port,
               'username', username,
               'password', password,
               'fromAddress', from_address,
               'fromName', from_name,
               'auth', auth,
               'starttls', starttls,
               'ssl', `ssl`
       ),
       status,
       create_time,
       update_time
FROM email_config;

DELETE FROM notification_config WHERE config_type = 'WECOM_APP';
INSERT INTO notification_config (config_name, config_type, config_json, status, create_time, update_time)
SELECT config_name,
       'WECOM_APP',
       JSON_OBJECT(
               'corpId', corp_id,
               'agentId', agent_id,
               'secret', secret,
               'token', token,
               'aesKey', aes_key,
               'proxyUrl', proxy_url,
               'menuJson', menu_json
       ),
       status,
       create_time,
       update_time
FROM wecom_app_config;

DELETE FROM notification_config WHERE config_type = 'WECOM_BOT';
INSERT INTO notification_config (config_name, config_type, config_json, status, create_time, update_time)
SELECT config_name,
       'WECOM_BOT',
       JSON_OBJECT('webhookKey', webhook_key),
       status,
       create_time,
       update_time
FROM wecom_bot_config;

DELETE FROM notification_config WHERE config_type = 'WECOM_INTELLIGENT_BOT';
INSERT INTO notification_config (config_name, config_type, config_json, status, create_time, update_time)
SELECT config_name,
       'WECOM_INTELLIGENT_BOT',
       JSON_OBJECT('webhookKey', webhook_key),
       status,
       create_time,
       update_time
FROM wecom_intelligent_bot_config;

DROP TABLE IF EXISTS email_config;
DROP TABLE IF EXISTS wecom_app_config;
DROP TABLE IF EXISTS wecom_bot_config;
DROP TABLE IF EXISTS wecom_intelligent_bot_config;
