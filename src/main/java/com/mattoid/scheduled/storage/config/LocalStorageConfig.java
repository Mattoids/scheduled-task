package com.mattoid.scheduled.storage.config;

import lombok.Data;

@Data
public class LocalStorageConfig {

    /**
     * 本地存储目录，相对于 report.upload.path 或绝对路径
     */
    private String storagePath;

    /**
     * 文件访问基础 URL，例如 http://localhost:1236/storage
     */
    private String baseUrl;
}
