package com.mattoid.scheduled.storage.client;

import com.mattoid.scheduled.storage.config.LocalStorageConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
public class LocalStorageClient implements StorageClient {

    private final LocalStorageConfig config;
    private final String uploadRoot;
    private final String baseUrl;

    public LocalStorageClient(LocalStorageConfig config, String uploadRoot, String baseUrl) {
        this.config = config;
        this.uploadRoot = uploadRoot;
        this.baseUrl = baseUrl;
    }

    @Override
    public String upload(File file, String filename) throws Exception {
        if (!StringUtils.hasText(filename)) {
            filename = file.getName();
        }
        filename = sanitizeFilename(filename);
        String dateFolder = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String folder = UUID.randomUUID().toString();
        String prefix = StringUtils.hasText(config.getStoragePath()) ? config.getStoragePath().trim() : "storage";
        if (prefix.startsWith("/")) {
            prefix = prefix.substring(1);
        }

        Path targetDir = Paths.get(uploadRoot, prefix, dateFolder, folder);
        if (!Files.exists(targetDir)) {
            Files.createDirectories(targetDir);
        }
        Path target = targetDir.resolve(filename);
        Files.copy(file.toPath(), target, StandardCopyOption.REPLACE_EXISTING);

        String urlBase = StringUtils.hasText(config.getBaseUrl()) ? config.getBaseUrl().trim() : baseUrl;
        if (urlBase.endsWith("/")) {
            urlBase = urlBase.substring(0, urlBase.length() - 1);
        }
        return urlBase + "/" + prefix + "/" + dateFolder + "/" + folder + "/" + filename;
    }

    @Override
    public void delete(String path) throws Exception {
        if (!StringUtils.hasText(path)) {
            return;
        }
        String urlBase = StringUtils.hasText(config.getBaseUrl()) ? config.getBaseUrl().trim() : baseUrl;
        if (path.startsWith(urlBase)) {
            String relative = path.substring(urlBase.length());
            if (relative.startsWith("/")) {
                relative = relative.substring(1);
            }
            Path target = Paths.get(uploadRoot, relative);
            Files.deleteIfExists(target);
        }
    }

    private String sanitizeFilename(String filename) {
        return filename.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
