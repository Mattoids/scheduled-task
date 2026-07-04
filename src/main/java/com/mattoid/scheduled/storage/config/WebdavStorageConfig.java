package com.mattoid.scheduled.storage.config;

import lombok.Data;

@Data
public class WebdavStorageConfig {

    /**
     * WebDAV 地址，例如 https://nas.example.com/dav/reports
     */
    private String url;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 存储路径前缀
     */
    private String prefix;

    /**
     * 文件访问基础 URL（用于替换 WebDAV 路径为外部下载地址）
     */
    private String baseUrl;
}
