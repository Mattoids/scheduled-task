-- V7: 根据已有的 id 关联回填编码字段（幂等）

-- ===================== notification_rule.config_code =====================
UPDATE notification_rule r
       JOIN notification_config c ON r.config_id = c.id
   SET r.config_code = c.config_code
 WHERE r.config_id IS NOT NULL
   AND (r.config_code IS NULL OR r.config_code = '')
   AND c.config_code IS NOT NULL;

-- ===================== task_sql_config.template_code / group_code =====================
UPDATE task_sql_config s
       JOIN report_template t ON s.template_id = t.id
   SET s.template_code = t.template_code
 WHERE s.template_id IS NOT NULL
   AND (s.template_code IS NULL OR s.template_code = '')
   AND t.template_code IS NOT NULL;

UPDATE task_sql_config s
       JOIN task_sql_group g ON s.group_id = g.id
   SET s.group_code = g.group_code
 WHERE s.group_id IS NOT NULL
   AND (s.group_code IS NULL OR s.group_code = '')
   AND g.group_code IS NOT NULL;

-- ===================== task_sql_relation.task_code / sql_code =====================
UPDATE task_sql_relation r
       JOIN task_config t ON r.task_id = t.id
   SET r.task_code = t.task_code
 WHERE r.task_id IS NOT NULL
   AND (r.task_code IS NULL OR r.task_code = '')
   AND t.task_code IS NOT NULL;

UPDATE task_sql_relation r
       JOIN task_sql_config s ON r.sql_id = s.id
   SET r.sql_code = s.sql_code
 WHERE r.sql_id IS NOT NULL
   AND (r.sql_code IS NULL OR r.sql_code = '')
   AND s.sql_code IS NOT NULL;
