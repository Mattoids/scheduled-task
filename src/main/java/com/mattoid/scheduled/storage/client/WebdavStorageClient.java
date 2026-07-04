package com.mattoid.scheduled.storage.client;

import com.github.sardine.Sardine;
import com.github.sardine.SardineFactory;
import com.mattoid.scheduled.storage.config.WebdavStorageConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.UUID;

@Slf4j
public class WebdavStorageClient implements StorageClient {

    private final WebdavStorageConfig config;
    private final Sardine sardine;

    public WebdavStorageClient(WebdavStorageConfig config) {
        this.config = config;
        this.sardine = SardineFactory.begin(config.getUsername(), config.getPassword());
    }

    @Override
    public String upload(File file, String filename) throws Exception {
        if (!StringUtils.hasText(filename)) {
            filename = file.getName();
        }
        filename = sanitizeFilename(filename);
        String folder = UUID.randomUUID().toString();
        String prefix = StringUtils.hasText(config.getPrefix()) ? config.getPrefix().trim() : "scheduled-task";
        if (prefix.startsWith("/")) {
            prefix = prefix.substring(1);
        }

        String baseUrl = config.getUrl().trim();
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }
        String relativePath = prefix + "/" + folder + "/" + filename;
        String fullUrl = baseUrl + relativePath;

        // 确保目录存在
        createDirectories(baseUrl + prefix + "/" + folder);

        try (InputStream is = Files.newInputStream(file.toPath())) {
            sardine.put(fullUrl, is, (String) null);
        }

        if (StringUtils.hasText(config.getBaseUrl())) {
            String publicBase = config.getBaseUrl().trim();
            if (!publicBase.endsWith("/")) {
                publicBase += "/";
            }
            return publicBase + relativePath;
        }
        return fullUrl;
    }

    @Override
    public void delete(String path) throws Exception {
        if (StringUtils.hasText(path)) {
            sardine.delete(path);
        }
    }

    private void createDirectories(String dirUrl) throws Exception {
        if (!sardine.exists(dirUrl)) {
            sardine.createDirectory(dirUrl);
        }
    }

    private String sanitizeFilename(String filename) {
        return filename.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
