package com.mattoid.scheduled.storage.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mattoid.scheduled.entity.StorageConfig;
import com.mattoid.scheduled.storage.config.LocalStorageConfig;
import com.mattoid.scheduled.storage.config.OssStorageConfig;
import com.mattoid.scheduled.storage.config.S3StorageConfig;
import com.mattoid.scheduled.storage.config.WebdavStorageConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class StorageClientFactory {

    @Value("${report.upload.path}")
    private String uploadPath;

    @Value("${server.port:1236}")
    private int serverPort;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<Long, StorageClient> clientCache = new ConcurrentHashMap<>();

    public StorageClient getClient(StorageConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("存储配置不能为空");
        }
        return clientCache.computeIfAbsent(config.getId(), id -> createClient(config));
    }

    public void invalidateCache(Long configId) {
        clientCache.remove(configId);
    }

    private StorageClient createClient(StorageConfig config) {
        try {
            switch (config.getStorageType()) {
                case "LOCAL" -> {
                    LocalStorageConfig localConfig = objectMapper.readValue(config.getConfigJson(), LocalStorageConfig.class);
                    String baseUrl = StringUtils.hasText(localConfig.getBaseUrl())
                            ? localConfig.getBaseUrl().trim()
                            : "http://localhost:" + serverPort + "/storage";
                    return new LocalStorageClient(localConfig, uploadPath, baseUrl);
                }
                case "OSS" -> {
                    OssStorageConfig ossConfig = objectMapper.readValue(config.getConfigJson(), OssStorageConfig.class);
                    return new OssStorageClient(ossConfig);
                }
                case "S3" -> {
                    S3StorageConfig s3Config = objectMapper.readValue(config.getConfigJson(), S3StorageConfig.class);
                    return new S3StorageClient(s3Config);
                }
                case "WEBDAV" -> {
                    WebdavStorageConfig webdavConfig = objectMapper.readValue(config.getConfigJson(), WebdavStorageConfig.class);
                    return new WebdavStorageClient(webdavConfig);
                }
                default -> throw new IllegalArgumentException("不支持的存储类型: " + config.getStorageType());
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("创建存储客户端失败: " + config.getId(), e);
        }
    }
}
