package com.mattoid.scheduled.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("datasource_config")
public class DatasourceConfig extends BaseEntity {

    private String name;
    private String dbType;
    private String host;
    private Integer port;
    private String databaseName;
    private String username;
    private String password;
    private String driverClass;
    private String jdbcUrlParams;

    /**
     * 是否启用 SSH 代理
     */
    private Integer sshEnabled;

    private String sshHost;
    private Integer sshPort;
    private String sshUsername;
    private String sshPassword;
    private String sshPrivateKey;
    private String sshPassphrase;

    /**
     * 本地代理端口（启动 SSH 隧道后填充）
     */
    private Integer sshLocalPort;

    /**
     * SSH 认证方式：password / key（非数据库字段，仅用于前后端交互）
     */
    @TableField(exist = false)
    private String sshAuthType;

    private String remark;

    /**
     * 数据源自定义 prompt：AI 生成 SQL 时与该数据源的数据字典文档一并注入，
     * 用于固化业务口径、固定过滤条件（如 is_delete=0）、表/字段偏好、时间口径等。
     */
    private String customPrompt;

    private Integer status;
}
