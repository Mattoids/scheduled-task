package com.mattoid.scheduled.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("email_config")
public class EmailConfig extends BaseEntity {

    private String configName;
    private String smtpHost;
    private Integer smtpPort;
    private String username;
    private String password;
    private String fromAddress;
    private String fromName;
    private Integer auth;
    private Integer starttls;
    private Integer ssl;
    private Integer status;
}
