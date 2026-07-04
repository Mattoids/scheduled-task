package com.mattoid.scheduled.storage.config;

import lombok.Data;

@Data
public class OssStorageConfig {

    /**
     * Endpoint，例如 https://oss-cn-hangzhou.aliyuncs.com
     */
    private String endpoint;

    /**
     * Access Key ID
     */
    private String accessKeyId;

    /**
     * Access Key Secret
     */
    private String accessKeySecret;

    /**
     * Bucket 名称
     */
    private String bucketName;

    /**
     * 存储路径前缀，例如 scheduled-task/reports
     */
    private String prefix;

    /**
     * 是否使用 HTTPS
     */
    private Boolean https = true;

    /**
     * 是否生成带签名的私有 URL（单位：秒）
     */
    private Long signedUrlExpires;
}
