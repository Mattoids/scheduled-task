-- 数据源支持自定义 prompt：AI 生成 SQL 时与该数据源的数据字典文档一并注入，用于固化业务口径、固定过滤条件与表/字段偏好。
ALTER TABLE `datasource_config`
    ADD COLUMN `custom_prompt` TEXT NULL COMMENT '数据源自定义 prompt：AI 生成 SQL 时与数据字典一并注入' AFTER `remark`;
