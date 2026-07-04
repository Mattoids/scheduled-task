package com.mattoid.scheduled.storage.config;

import lombok.Data;

@Data
public class S3StorageConfig {

    /**
     * Endpoint，例如 https://s3.amazonaws.com 或 MinIO 地址
     */
    private String endpoint;

    /**
     * Access Key ID
     */
    private String accessKeyId;

    /**
     * Secret Access Key
     */
    private String secretAccessKey;

    /**
     * Region，例如 us-east-1；MinIO 可填 us-east-1
     */
    private String region;

    /**
     * Bucket 名称
     */
    private String bucketName;

    /**
     * 存储路径前缀
     */
    private String prefix;

    /**
     * 是否启用路径样式访问（MinIO 通常需要）
     */
    private Boolean pathStyleAccess = false;

    /**
     * 签名 URL 过期时间（单位：秒），为空则使用公共 URL
     */
    private Long signedUrlExpires;
}
