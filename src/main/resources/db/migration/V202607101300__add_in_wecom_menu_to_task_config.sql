ALTER TABLE task_config
    ADD COLUMN in_wecom_menu TINYINT DEFAULT 0 COMMENT '是否加入企业微信应用菜单：1 是，0 否';
