package com.mattoid.scheduled.storage.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mattoid.scheduled.entity.StorageConfig;
import com.mattoid.scheduled.storage.client.StorageClient;
import com.mattoid.scheduled.storage.client.StorageClientFactory;
import com.mattoid.scheduled.storage.mapper.StorageConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.file.Files;

@Slf4j
@Service
public class StorageConfigService extends ServiceImpl<StorageConfigMapper, StorageConfig> {

    private final StorageClientFactory storageClientFactory;

    public StorageConfigService(StorageClientFactory storageClientFactory) {
        this.storageClientFactory = storageClientFactory;
    }

    public StorageConfig getDefaultConfig() {
        return lambdaQuery()
                .eq(StorageConfig::getStatus, 1)
                .eq(StorageConfig::getIsDefault, 1)
                .one();
    }

    /**
     * 获取实际使用的存储配置。若指定了 ID 且存在/启用则优先使用，否则回退到默认配置。
     */
    public StorageConfig getEffectiveConfig(Long storageConfigId) {
        if (storageConfigId != null) {
            StorageConfig config = getById(storageConfigId);
            if (config != null && config.getStatus() != null && config.getStatus() == 1) {
                return config;
            }
            log.warn("指定的存储配置不存在或已禁用: {}, 尝试使用默认配置", storageConfigId);
        }
        return getDefaultConfig();
    }

    public StorageClient getClient(Long storageConfigId) {
        StorageConfig config = getEffectiveConfig(storageConfigId);
        if (config == null) {
            throw new IllegalArgumentException("没有可用的存储配置");
        }
        return storageClientFactory.getClient(config);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean saveOrUpdateConfig(StorageConfig config) {
        if (config.getIsDefault() != null && config.getIsDefault() == 1) {
            lambdaUpdate()
                    .set(StorageConfig::getIsDefault, 0)
                    .ne(config.getId() != null, StorageConfig::getId, config.getId())
                    .update();
        }
        boolean result = saveOrUpdate(config);
        if (config.getId() != null) {
            storageClientFactory.invalidateCache(config.getId());
        }
        return result;
    }

    public String testConfig(Long id) {
        StorageConfig config = getById(id);
        if (config == null) {
            return "存储配置不存在";
        }
        try {
            StorageClient client = storageClientFactory.getClient(config);
            File tempFile = Files.createTempFile("storage-test-", ".txt").toFile();
            Files.writeString(tempFile.toPath(), "storage test");
            try {
                String url = client.upload(tempFile, "storage-test.txt");
                log.info("存储配置测试上传成功: configId={}, url={}", id, url);
                return "测试成功，文件地址: " + url;
            } finally {
                client.delete(tempFile.getName());
                Files.deleteIfExists(tempFile.toPath());
            }
        } catch (Exception e) {
            log.error("存储配置测试失败: configId={}", id, e);
            return "测试失败: " + e.getMessage();
        }
    }
}
