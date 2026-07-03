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
    private Integer status;
}
