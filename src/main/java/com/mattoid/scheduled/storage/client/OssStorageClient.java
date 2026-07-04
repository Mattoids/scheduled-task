package com.mattoid.scheduled.storage.client;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.mattoid.scheduled.storage.config.OssStorageConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.File;
import java.net.URL;
import java.util.Date;
import java.util.UUID;

@Slf4j
public class OssStorageClient implements StorageClient {

    private final OssStorageConfig config;
    private final OSS ossClient;

    public OssStorageClient(OssStorageConfig config) {
        this.config = config;
        ClientBuilderConfiguration clientConfig = new ClientBuilderConfiguration();
        this.ossClient = new OSSClientBuilder()
                .build(config.getEndpoint(), config.getAccessKeyId(), config.getAccessKeySecret(), clientConfig);
    }

    @Override
    public String upload(File file, String filename) throws Exception {
        if (!StringUtils.hasText(filename)) {
            filename = file.getName();
        }
        filename = sanitizeFilename(filename);
        String objectKey = buildObjectKey(filename);
        ossClient.putObject(config.getBucketName(), objectKey, file);
        return buildUrl(objectKey);
    }

    @Override
    public void delete(String path) throws Exception {
        String objectKey = extractObjectKey(path);
        if (StringUtils.hasText(objectKey)) {
            ossClient.deleteObject(config.getBucketName(), objectKey);
        }
    }

    private String buildObjectKey(String filename) {
        String prefix = StringUtils.hasText(config.getPrefix()) ? config.getPrefix().trim() : "scheduled-task";
        if (prefix.startsWith("/")) {
            prefix = prefix.substring(1);
        }
        return prefix + "/" + UUID.randomUUID() + "/" + filename;
    }

    private String buildUrl(String objectKey) {
        Long expires = config.getSignedUrlExpires();
        if (expires != null && expires > 0) {
            Date expiration = new Date(System.currentTimeMillis() + expires * 1000);
            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(config.getBucketName(), objectKey);
            request.setExpiration(expiration);
            URL url = ossClient.generatePresignedUrl(request);
            return url.toString();
        }

        URL endpointUrl;
        try {
            endpointUrl = new URL(config.getEndpoint());
        } catch (Exception e) {
            throw new IllegalArgumentException("无效的 OSS Endpoint: " + config.getEndpoint(), e);
        }
        String protocol = Boolean.FALSE.equals(config.getHttps()) ? "http" : endpointUrl.getProtocol();
        return protocol + "://" + config.getBucketName() + "." + endpointUrl.getHost() + "/" + objectKey;
    }

    private String extractObjectKey(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        URL endpointUrl;
        try {
            endpointUrl = new URL(config.getEndpoint());
        } catch (Exception e) {
            return null;
        }
        String prefix = config.getBucketName() + "." + endpointUrl.getHost();
        int idx = url.indexOf(prefix);
        if (idx >= 0) {
            return url.substring(idx + prefix.length());
        }
        // 兼容签名 URL 中 bucket.host 后面跟 ? 的情况
        int hostIdx = url.indexOf(endpointUrl.getHost());
        if (hostIdx >= 0) {
            int afterHost = hostIdx + endpointUrl.getHost().length();
            return url.substring(afterHost);
        }
        return null;
    }

    private String sanitizeFilename(String filename) {
        return filename.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
