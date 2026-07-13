package com.mattoid.scheduled.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 企业微信管理账户。
 * <p>「企业应用管理」菜单使用，每个账户对应一个企业微信管理后台 Cookie，
 * 系统通过定时随机访问企业微信页面保活 Cookie，并支持免登录跳转。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wecom_admin_account")
public class WeComAdminAccount extends BaseEntity {

    /** 账户自定义名称 */
    private String accountName;

    /** 企业微信管理后台 Cookie（AES 加密存储，ENC 前缀） */
    private String adminCookie;

    /** 状态：1 启用 / 0 停用 */
    private Integer status;

    /** 是否启用 Cookie 保活定时任务 */
    private Boolean keepAliveEnabled;

    /** 最近一次保活访问时间 */
    private LocalDateTime lastKeepAliveTime;

    /** 最近一次保活结果描述 */
    private String lastKeepAliveResult;

    /** 是否已配置 Cookie（列表展示用，非数据库字段；返回列表时 Cookie 本身不下发） */
    @TableField(exist = false)
    private Boolean cookieConfigured;
}
