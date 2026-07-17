package com.mattoid.scheduled.notification.support;

import com.mattoid.scheduled.storage.client.StorageClient;
import com.mattoid.scheduled.storage.service.StorageConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 通知附件上传辅助组件，将报告文件上传至已配置的存储系统并返回可访问链接。
 */
@Slf4j
@Component
public class NotificationFileUploader {

    private final StorageConfigService storageConfigService;

    public NotificationFileUploader(StorageConfigService storageConfigService) {
        this.storageConfigService = storageConfigService;
    }

    public List<String> upload(Long storageConfigId, List<File> reportFiles) {
        List<String> urls = new ArrayList<>();
        if (storageConfigId == null || reportFiles == null || reportFiles.isEmpty()) {
            return urls;
        }
        try {
            StorageClient client = storageConfigService.getClient(storageConfigId);
            for (File file : reportFiles) {
                if (file == null || !file.exists()) {
                    continue;
                }
                String url = client.upload(file, file.getName());
                urls.add(url);
                log.info("文件已上传至存储系统: configId={}, file={}, url={}", storageConfigId, file.getName(), url);
            }
        } catch (Exception e) {
            log.error("上传文件到存储系统失败: configId={}", storageConfigId, e);
        }
        return urls;
    }
}
