-- 合并迁移：Quartz 表、通知日志、审计日志、任务依赖、SQL 图表配置
-- 由旧版 V3 ~ V11 合并而来，保留 V1、V2 不变

-- ===================== V6: 更新通知配置/规则表注释，纳入新增渠道 =====================

ALTER TABLE notification_config
    MODIFY COLUMN config_type VARCHAR(32) NOT NULL COMMENT 'EMAIL / WECOM_APP / WECOM_BOT / WECOM_INTELLIGENT_BOT / DINGTALK / FEISHU / SLACK / WEBHOOK';

ALTER TABLE notification_rule
    MODIFY COLUMN channel VARCHAR(32) NOT NULL COMMENT 'EMAIL / WECOM_APP / WECOM_BOT / WECOM_INTELLIGENT_BOT / DINGTALK / FEISHU / SLACK / WEBHOOK';

-- ===================== V8: Quartz JDBC JobStore 表结构 =====================

CREATE TABLE IF NOT EXISTS QRTZ_JOB_DETAILS (
    SCHED_NAME VARCHAR(120) NOT NULL,
    JOB_NAME VARCHAR(190) NOT NULL,
    JOB_GROUP VARCHAR(190) NOT NULL,
    DESCRIPTION VARCHAR(250) NULL,
    JOB_CLASS_NAME VARCHAR(250) NOT NULL,
    IS_DURABLE VARCHAR(1) NOT NULL,
    IS_NONCONCURRENT VARCHAR(1) NOT NULL,
    IS_UPDATE_DATA VARCHAR(1) NOT NULL,
    REQUESTS_RECOVERY VARCHAR(1) NOT NULL,
    JOB_DATA BLOB NULL,
    PRIMARY KEY (SCHED_NAME, JOB_NAME, JOB_GROUP)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS QRTZ_TRIGGERS (
    SCHED_NAME VARCHAR(120) NOT NULL,
    TRIGGER_NAME VARCHAR(190) NOT NULL,
    TRIGGER_GROUP VARCHAR(190) NOT NULL,
    JOB_NAME VARCHAR(190) NOT NULL,
    JOB_GROUP VARCHAR(190) NOT NULL,
    DESCRIPTION VARCHAR(250) NULL,
    NEXT_FIRE_TIME BIGINT(13) NULL,
    PREV_FIRE_TIME BIGINT(13) NULL,
    PRIORITY INTEGER NULL,
    TRIGGER_STATE VARCHAR(16) NOT NULL,
    TRIGGER_TYPE VARCHAR(8) NOT NULL,
    START_TIME BIGINT(13) NOT NULL,
    END_TIME BIGINT(13) NULL,
    CALENDAR_NAME VARCHAR(190) NULL,
    MISFIRE_INSTR SMALLINT(2) NULL,
    JOB_DATA BLOB NULL,
    PRIMARY KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP),
    FOREIGN KEY (SCHED_NAME, JOB_NAME, JOB_GROUP) REFERENCES QRTZ_JOB_DETAILS(SCHED_NAME, JOB_NAME, JOB_GROUP)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS QRTZ_SIMPLE_TRIGGERS (
    SCHED_NAME VARCHAR(120) NOT NULL,
    TRIGGER_NAME VARCHAR(190) NOT NULL,
    TRIGGER_GROUP VARCHAR(190) NOT NULL,
    REPEAT_COUNT BIGINT(7) NOT NULL,
    REPEAT_INTERVAL BIGINT(12) NOT NULL,
    TIMES_TRIGGERED BIGINT(10) NOT NULL,
    PRIMARY KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP),
    FOREIGN KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP) REFERENCES QRTZ_TRIGGERS(SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS QRTZ_CRON_TRIGGERS (
    SCHED_NAME VARCHAR(120) NOT NULL,
    TRIGGER_NAME VARCHAR(190) NOT NULL,
    TRIGGER_GROUP VARCHAR(190) NOT NULL,
    CRON_EXPRESSION VARCHAR(120) NOT NULL,
    TIME_ZONE_ID VARCHAR(80),
    PRIMARY KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP),
    FOREIGN KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP) REFERENCES QRTZ_TRIGGERS(SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS QRTZ_SIMPROP_TRIGGERS (
    SCHED_NAME VARCHAR(120) NOT NULL,
    TRIGGER_NAME VARCHAR(190) NOT NULL,
    TRIGGER_GROUP VARCHAR(190) NOT NULL,
    STR_PROP_1 VARCHAR(512) NULL,
    STR_PROP_2 VARCHAR(512) NULL,
    STR_PROP_3 VARCHAR(512) NULL,
    INT_PROP_1 INT NULL,
    INT_PROP_2 INT NULL,
    LONG_PROP_1 BIGINT NULL,
    LONG_PROP_2 BIGINT NULL,
    DEC_PROP_1 NUMERIC(13,4) NULL,
    DEC_PROP_2 NUMERIC(13,4) NULL,
    BOOL_PROP_1 VARCHAR(1) NULL,
    BOOL_PROP_2 VARCHAR(1) NULL,
    TIME_ZONE_ID VARCHAR(80) NULL,
    PRIMARY KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP),
    FOREIGN KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP) REFERENCES QRTZ_TRIGGERS(SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS QRTZ_BLOB_TRIGGERS (
    SCHED_NAME VARCHAR(120) NOT NULL,
    TRIGGER_NAME VARCHAR(190) NOT NULL,
    TRIGGER_GROUP VARCHAR(190) NOT NULL,
    BLOB_DATA BLOB NULL,
    PRIMARY KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP),
    FOREIGN KEY (SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP) REFERENCES QRTZ_TRIGGERS(SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS QRTZ_CALENDARS (
    SCHED_NAME VARCHAR(120) NOT NULL,
    CALENDAR_NAME VARCHAR(190) NOT NULL,
    CALENDAR BLOB NOT NULL,
    PRIMARY KEY (SCHED_NAME, CALENDAR_NAME)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS QRTZ_PAUSED_TRIGGER_GRPS (
    SCHED_NAME VARCHAR(120) NOT NULL,
    TRIGGER_GROUP VARCHAR(190) NOT NULL,
    PRIMARY KEY (SCHED_NAME, TRIGGER_GROUP)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS QRTZ_SCHEDULER_STATE (
    SCHED_NAME VARCHAR(120) NOT NULL,
    INSTANCE_NAME VARCHAR(190) NOT NULL,
    LAST_CHECKIN_TIME BIGINT(13) NOT NULL,
    CHECKIN_INTERVAL BIGINT(13) NOT NULL,
    PRIMARY KEY (SCHED_NAME, INSTANCE_NAME)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS QRTZ_LOCKS (
    SCHED_NAME VARCHAR(120) NOT NULL,
    LOCK_NAME VARCHAR(40) NOT NULL,
    PRIMARY KEY (SCHED_NAME, LOCK_NAME)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS QRTZ_FIRED_TRIGGERS (
    SCHED_NAME VARCHAR(120) NOT NULL,
    ENTRY_ID VARCHAR(95) NOT NULL,
    TRIGGER_NAME VARCHAR(190) NOT NULL,
    TRIGGER_GROUP VARCHAR(190) NOT NULL,
    INSTANCE_NAME VARCHAR(190) NOT NULL,
    FIRED_TIME BIGINT(13) NOT NULL,
    SCHED_TIME BIGINT(13) NOT NULL,
    PRIORITY INTEGER NOT NULL,
    STATE VARCHAR(16) NOT NULL,
    JOB_NAME VARCHAR(190) NULL,
    JOB_GROUP VARCHAR(190) NULL,
    IS_NONCONCURRENT VARCHAR(1) NULL,
    REQUESTS_RECOVERY VARCHAR(1) NULL,
    PRIMARY KEY (SCHED_NAME, ENTRY_ID)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX IDX_QRTZ_J_REQ_RECOVERY ON QRTZ_JOB_DETAILS(SCHED_NAME, REQUESTS_RECOVERY);
CREATE INDEX IDX_QRTZ_J_GRP ON QRTZ_JOB_DETAILS(SCHED_NAME, JOB_GROUP);
CREATE INDEX IDX_QRTZ_T_J ON QRTZ_TRIGGERS(SCHED_NAME, JOB_NAME, JOB_GROUP);
CREATE INDEX IDX_QRTZ_T_JG ON QRTZ_TRIGGERS(SCHED_NAME, JOB_GROUP);
CREATE INDEX IDX_QRTZ_T_C ON QRTZ_TRIGGERS(SCHED_NAME, CALENDAR_NAME);
CREATE INDEX IDX_QRTZ_T_G ON QRTZ_TRIGGERS(SCHED_NAME, TRIGGER_GROUP);
CREATE INDEX IDX_QRTZ_T_STATE ON QRTZ_TRIGGERS(SCHED_NAME, TRIGGER_STATE);
CREATE INDEX IDX_QRTZ_T_N_STATE ON QRTZ_TRIGGERS(SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP, TRIGGER_STATE);
CREATE INDEX IDX_QRTZ_T_N_G_STATE ON QRTZ_TRIGGERS(SCHED_NAME, TRIGGER_GROUP, TRIGGER_STATE);
CREATE INDEX IDX_QRTZ_T_NEXT_FIRE_TIME ON QRTZ_TRIGGERS(SCHED_NAME, NEXT_FIRE_TIME);
CREATE INDEX IDX_QRTZ_T_NFT_ST ON QRTZ_TRIGGERS(SCHED_NAME, TRIGGER_STATE, NEXT_FIRE_TIME);
CREATE INDEX IDX_QRTZ_T_NFT_MISFIRE ON QRTZ_TRIGGERS(SCHED_NAME, MISFIRE_INSTR, NEXT_FIRE_TIME);
CREATE INDEX IDX_QRTZ_T_NFT_ST_MISFIRE ON QRTZ_TRIGGERS(SCHED_NAME, MISFIRE_INSTR, NEXT_FIRE_TIME, TRIGGER_STATE);
CREATE INDEX IDX_QRTZ_T_NFT_ST_MISFIRE_GRP ON QRTZ_TRIGGERS(SCHED_NAME, MISFIRE_INSTR, NEXT_FIRE_TIME, TRIGGER_GROUP, TRIGGER_STATE);
CREATE INDEX IDX_QRTZ_FT_TRIG_INST_NAME ON QRTZ_FIRED_TRIGGERS(SCHED_NAME, INSTANCE_NAME);
CREATE INDEX IDX_QRTZ_FT_INST_JOB_REQ_RCVRY ON QRTZ_FIRED_TRIGGERS(SCHED_NAME, INSTANCE_NAME, REQUESTS_RECOVERY);
CREATE INDEX IDX_QRTZ_FT_J_G ON QRTZ_FIRED_TRIGGERS(SCHED_NAME, JOB_NAME, JOB_GROUP);
CREATE INDEX IDX_QRTZ_FT_JG ON QRTZ_FIRED_TRIGGERS(SCHED_NAME, JOB_GROUP);
CREATE INDEX IDX_QRTZ_FT_T_G ON QRTZ_FIRED_TRIGGERS(SCHED_NAME, TRIGGER_NAME, TRIGGER_GROUP);
CREATE INDEX IDX_QRTZ_FT_TG ON QRTZ_FIRED_TRIGGERS(SCHED_NAME, TRIGGER_GROUP);

-- ===================== V4: 通知日志 =====================

CREATE TABLE IF NOT EXISTS notification_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT COMMENT '关联任务 ID',
    event_type VARCHAR(32) COMMENT 'TASK_SUCCESS / TASK_FAILURE / TASK_COMPLETED',
    rule_id BIGINT COMMENT '通知规则 ID',
    channel VARCHAR(32) COMMENT 'EMAIL / WECOM_APP / WECOM_BOT / WECOM_INTELLIGENT_BOT',
    config_id BIGINT COMMENT '通知配置 ID',
    recipient VARCHAR(1024) COMMENT '接收人，邮箱或企业微信用户',
    content TEXT COMMENT '发送内容摘要',
    status VARCHAR(32) COMMENT 'SUCCESS / FAILED',
    error_message TEXT COMMENT '失败原因',
    retry_count INT DEFAULT 0 COMMENT '当前重试次数',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @idx_exists = (SELECT COUNT(1) FROM information_schema.statistics
                   WHERE table_schema = DATABASE()
                     AND table_name = 'notification_log'
                     AND index_name = 'idx_notification_log_task_id');
SET @sql = IF(@idx_exists = 0, 'CREATE INDEX idx_notification_log_task_id ON notification_log(task_id)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(1) FROM information_schema.statistics
                   WHERE table_schema = DATABASE()
                     AND table_name = 'notification_log'
                     AND index_name = 'idx_notification_log_rule_id');
SET @sql = IF(@idx_exists = 0, 'CREATE INDEX idx_notification_log_rule_id ON notification_log(rule_id)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(1) FROM information_schema.statistics
                   WHERE table_schema = DATABASE()
                     AND table_name = 'notification_log'
                     AND index_name = 'idx_notification_log_status');
SET @sql = IF(@idx_exists = 0, 'CREATE INDEX idx_notification_log_status ON notification_log(status)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(1) FROM information_schema.statistics
                   WHERE table_schema = DATABASE()
                     AND table_name = 'notification_log'
                     AND index_name = 'idx_notification_log_create_time');
SET @sql = IF(@idx_exists = 0, 'CREATE INDEX idx_notification_log_create_time ON notification_log(create_time)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ===================== V5: 操作审计日志 =====================

CREATE TABLE IF NOT EXISTS operation_audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    operator VARCHAR(128) COMMENT '操作人',
    operation_type VARCHAR(32) COMMENT '操作类型: CREATE/UPDATE/DELETE/EXECUTE/LOGIN/LOGOUT',
    resource_type VARCHAR(64) COMMENT '资源类型: TASK/SQL_CONFIG/DATASOURCE/NOTIFICATION/etc',
    resource_id BIGINT COMMENT '资源 ID',
    resource_name VARCHAR(512) COMMENT '资源名称',
    request_uri VARCHAR(512) COMMENT '请求 URI',
    request_method VARCHAR(16) COMMENT '请求方法',
    request_params TEXT COMMENT '请求参数',
    old_value TEXT COMMENT '变更前数据快照(JSON)',
    new_value TEXT COMMENT '变更后数据快照(JSON)',
    ip_address VARCHAR(64) COMMENT '操作人 IP',
    status VARCHAR(16) COMMENT 'SUCCESS / FAILED',
    error_message TEXT COMMENT '失败原因',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

SET @idx_exists = (SELECT COUNT(1) FROM information_schema.statistics
                   WHERE table_schema = DATABASE()
                     AND table_name = 'operation_audit_log'
                     AND index_name = 'idx_audit_operator');
SET @sql = IF(@idx_exists = 0, 'CREATE INDEX idx_audit_operator ON operation_audit_log(operator)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(1) FROM information_schema.statistics
                   WHERE table_schema = DATABASE()
                     AND table_name = 'operation_audit_log'
                     AND index_name = 'idx_audit_resource');
SET @sql = IF(@idx_exists = 0, 'CREATE INDEX idx_audit_resource ON operation_audit_log(resource_type, resource_id)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(1) FROM information_schema.statistics
                   WHERE table_schema = DATABASE()
                     AND table_name = 'operation_audit_log'
                     AND index_name = 'idx_audit_operation_type');
SET @sql = IF(@idx_exists = 0, 'CREATE INDEX idx_audit_operation_type ON operation_audit_log(operation_type)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(1) FROM information_schema.statistics
                   WHERE table_schema = DATABASE()
                     AND table_name = 'operation_audit_log'
                     AND index_name = 'idx_audit_status');
SET @sql = IF(@idx_exists = 0, 'CREATE INDEX idx_audit_status ON operation_audit_log(status)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_exists = (SELECT COUNT(1) FROM information_schema.statistics
                   WHERE table_schema = DATABASE()
                     AND table_name = 'operation_audit_log'
                     AND index_name = 'idx_audit_create_time');
SET @sql = IF(@idx_exists = 0, 'CREATE INDEX idx_audit_create_time ON operation_audit_log(create_time)', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ===================== V7: 任务依赖 =====================

CREATE TABLE IF NOT EXISTS task_dependency (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id BIGINT NOT NULL COMMENT '任务 ID',
    depends_on_task_id BIGINT NOT NULL COMMENT '依赖的任务 ID',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_task_dependency (task_id, depends_on_task_id),
    INDEX idx_task_dependency_task_id (task_id),
    INDEX idx_task_dependency_depends_on (depends_on_task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ===================== V10: 将图表配置从通知规则迁移到 SQL 配置 =====================

ALTER TABLE task_sql_config
    ADD COLUMN chart_enabled TINYINT DEFAULT 0 COMMENT '是否根据该 SQL 结果生成图表：1 启用，0 禁用',
    ADD COLUMN chart_type VARCHAR(16) DEFAULT 'BAR' COMMENT '图表类型：BAR / LINE / PIE',
    ADD COLUMN chart_title VARCHAR(256) NULL COMMENT '图表标题，留空使用 sql_name';

-- 兼容未执行过 V9 的环境：先判断列是否存在再删除
SET @col_exists = (SELECT COUNT(1) FROM information_schema.columns
                   WHERE table_schema = DATABASE()
                     AND table_name = 'notification_rule'
                     AND column_name = 'chart_enabled');
SET @sql = IF(@col_exists = 1, 'ALTER TABLE notification_rule DROP COLUMN chart_enabled', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(1) FROM information_schema.columns
                   WHERE table_schema = DATABASE()
                     AND table_name = 'notification_rule'
                     AND column_name = 'chart_type');
SET @sql = IF(@col_exists = 1, 'ALTER TABLE notification_rule DROP COLUMN chart_type', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(1) FROM information_schema.columns
                   WHERE table_schema = DATABASE()
                     AND table_name = 'notification_rule'
                     AND column_name = 'chart_title');
SET @sql = IF(@col_exists = 1, 'ALTER TABLE notification_rule DROP COLUMN chart_title', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_exists = (SELECT COUNT(1) FROM information_schema.columns
                   WHERE table_schema = DATABASE()
                     AND table_name = 'notification_rule'
                     AND column_name = 'chart_sql_index');
SET @sql = IF(@col_exists = 1, 'ALTER TABLE notification_rule DROP COLUMN chart_sql_index', 'SELECT 1');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
