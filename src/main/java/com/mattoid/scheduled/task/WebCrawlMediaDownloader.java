package com.mattoid.scheduled.task;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mattoid.scheduled.datasource.SshTunnel;
import com.mattoid.scheduled.entity.TaskWebCrawlConfig;
import com.mattoid.scheduled.storage.client.StorageClient;
import com.mattoid.scheduled.storage.service.StorageConfigService;
import com.mattoid.scheduled.util.PlaceholderUtils;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Component
public class WebCrawlMediaDownloader {

    private static final String DEFAULT_MEDIA_SELECTOR = "img,video,audio,source";

    private final ObjectMapper objectMapper;
    private final StorageConfigService storageConfigService;

    @Value("${report.upload.path}")
    private String uploadPath;

    public WebCrawlMediaDownloader(ObjectMapper objectMapper, StorageConfigService storageConfigService) {
        this.objectMapper = objectMapper;
        this.storageConfigService = storageConfigService;
    }

    public MediaResult download(Document document, TaskWebCrawlConfig config,
                                Map<String, Object> params, SshTunnel tunnel) throws Exception {
        if (!Integer.valueOf(1).equals(config.getMediaEnabled())) {
            return MediaResult.empty();
        }
        String selector = StringUtils.hasText(config.getMediaSelector())
                ? config.getMediaSelector() : DEFAULT_MEDIA_SELECTOR;
        Elements elements = document.select(selector);
        String baseUrl = document.baseUri();
        Set<String> urls = new LinkedHashSet<>();
        for (Element element : elements) {
            collectMediaUrls(element, baseUrl, urls);
        }

        MediaFilter filter = parseMediaFilter(config.getMediaFilterConfig());
        List<File> downloaded = new ArrayList<>();
        for (String mediaUrl : urls) {
            String actualUrl = applySshTunnelToUrl(mediaUrl, tunnel);
            try {
                File file = downloadFile(actualUrl);
                if (!filter.accept(file, mediaUrl)) {
                    Files.deleteIfExists(file.toPath());
                    continue;
                }
                downloaded.add(file);
            } catch (Exception e) {
                log.warn("下载媒体失败: {} - {}", mediaUrl, e.getMessage());
            }
        }

        String outputMode = config.getMediaOutputMode();
        if ("STORE_ONLY".equalsIgnoreCase(outputMode) || "ATTACH_ZIP".equalsIgnoreCase(outputMode)) {
            uploadToStorage(downloaded, config.getMediaStorageConfigId());
        }

        if ("ZIP".equalsIgnoreCase(outputMode) || "ATTACH_ZIP".equalsIgnoreCase(outputMode)) {
            File zipFile = packZip(downloaded, config, params);
            return new MediaResult(Collections.singletonList(zipFile), downloaded.size());
        }

        return new MediaResult(downloaded, downloaded.size());
    }

    private void collectMediaUrls(Element element, String baseUrl, Set<String> urls) {
        String src = element.hasAttr("data-src") ? element.attr("data-src") : element.attr("src");
        if (!StringUtils.hasText(src)) {
            src = element.attr("srcset").split(",")[0].trim().split(" ")[0];
        }
        if (!StringUtils.hasText(src)) {
            return;
        }
        String absolute = resolveAbsoluteUrl(baseUrl, src);
        if (StringUtils.hasText(absolute)) {
            urls.add(absolute);
        }
    }

    private File downloadFile(String url) throws Exception {
        URL parsed = new URL(url);
        String fileName = Paths.get(parsed.getPath()).getFileName().toString();
        if (!StringUtils.hasText(fileName)) {
            fileName = "media_" + System.currentTimeMillis();
        }
        Path temp = Files.createTempFile("media_", "_" + fileName);
        try (InputStream in = parsed.openStream()) {
            Files.copy(in, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return temp.toFile();
    }

    private boolean shouldAcceptType(String url, String allowedTypes) {
        if (!StringUtils.hasText(allowedTypes)) {
            return true;
        }
        String lower = url.toLowerCase();
        for (String type : allowedTypes.split(",")) {
            String t = type.trim().toLowerCase();
            if (lower.contains("." + t) || lower.contains("/" + t)) {
                return true;
            }
        }
        return false;
    }

    private void uploadToStorage(List<File> files, Long storageConfigId) {
        if (files.isEmpty()) {
            return;
        }
        try {
            StorageClient client = storageConfigService.getClient(storageConfigId);
            for (File file : files) {
                client.upload(file, file.getName());
            }
        } catch (Exception e) {
            log.error("上传媒体到存储失败: {}", e.getMessage(), e);
            throw new RuntimeException("上传媒体到存储失败: " + e.getMessage(), e);
        }
    }

    private File packZip(List<File> files, TaskWebCrawlConfig config, Map<String, Object> params) throws Exception {
        Path outputDir = Paths.get(uploadPath, "reports", "crawl", String.valueOf(config.getId()));
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }
        String pattern = StringUtils.hasText(config.getMediaZipNamePattern())
                ? config.getMediaZipNamePattern() : "media_{yyyyMMddHHmmss}";
        String fileName = PlaceholderUtils.replacePlaceholders(pattern, params) + ".zip";
        fileName = fileName.replaceAll("[\\\\/:*?\"\u003c\u003e|]", "_");
        Path zipPath = outputDir.resolve(fileName);
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            for (File file : files) {
                ZipEntry entry = new ZipEntry(file.getName());
                zos.putNextEntry(entry);
                Files.copy(file.toPath(), zos);
                zos.closeEntry();
            }
        }
        return zipPath.toFile();
    }

    private MediaFilter parseMediaFilter(String json) {
        if (!StringUtils.hasText(json)) {
            return new MediaFilter();
        }
        try {
            return objectMapper.readValue(json, MediaFilter.class);
        } catch (Exception e) {
            log.warn("解析媒体筛选配置失败: {}", e.getMessage());
            return new MediaFilter();
        }
    }

    private String applySshTunnelToUrl(String url, SshTunnel tunnel) {
        if (tunnel == null || !StringUtils.hasText(url)) {
            return url;
        }
        try {
            URL parsed = new URL(url);
            return new URL(parsed.getProtocol(), "127.0.0.1", tunnel.getLocalPort(), parsed.getFile()).toString();
        } catch (MalformedURLException e) {
            log.warn("替换 SSH 隧道 URL 失败: {}", url, e);
            return url;
        }
    }

    private String resolveAbsoluteUrl(String baseUrl, String relativeUrl) {
        if (!StringUtils.hasText(relativeUrl)) {
            return null;
        }
        if (relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://")) {
            return relativeUrl;
        }
        try {
            return new URL(new URL(baseUrl), relativeUrl).toString();
        } catch (MalformedURLException e) {
            return relativeUrl;
        }
    }

    public record MediaResult(List<File> files, int count) {
        public static MediaResult empty() {
            return new MediaResult(Collections.emptyList(), 0);
        }
    }

    public static class MediaFilter {
        private Long maxFileSizeBytes;
        private Long minFileSizeBytes;
        private Integer minWidth;
        private Integer minHeight;
        private Integer maxWidth;
        private Integer maxHeight;
        private List<String> allowedMimeTypes;
        private List<String> denyMimeTypes;

        public boolean accept(File file, String url) throws Exception {
            long size = file.length();
            if (minFileSizeBytes != null && size < minFileSizeBytes) {
                return false;
            }
            if (maxFileSizeBytes != null && size > maxFileSizeBytes) {
                return false;
            }
            String contentType = Files.probeContentType(file.toPath());
            if (contentType == null) {
                contentType = guessContentType(url);
            }
            if (allowedMimeTypes != null && !allowedMimeTypes.isEmpty()) {
                if (!allowedMimeTypes.stream().anyMatch(contentType::contains)) {
                    return false;
                }
            }
            if (denyMimeTypes != null && !denyMimeTypes.isEmpty()) {
                if (denyMimeTypes.stream().anyMatch(contentType::contains)) {
                    return false;
                }
            }
            if (isImage(contentType)) {
                BufferedImage image = ImageIO.read(file);
                if (image != null) {
                    int width = image.getWidth();
                    int height = image.getHeight();
                    if (minWidth != null && width < minWidth) return false;
                    if (maxWidth != null && width > maxWidth) return false;
                    if (minHeight != null && height < minHeight) return false;
                    if (maxHeight != null && height > maxHeight) return false;
                }
            }
            return true;
        }

        private String guessContentType(String url) {
            String lower = url.toLowerCase();
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
            if (lower.endsWith(".png")) return "image/png";
            if (lower.endsWith(".gif")) return "image/gif";
            if (lower.endsWith(".webp")) return "image/webp";
            if (lower.endsWith(".mp4")) return "video/mp4";
            if (lower.endsWith(".mp3")) return "audio/mpeg";
            return "application/octet-stream";
        }

        private boolean isImage(String contentType) {
            return contentType != null && contentType.startsWith("image/");
        }

        public Long getMaxFileSizeBytes() { return maxFileSizeBytes; }
        public void setMaxFileSizeBytes(Long maxFileSizeBytes) { this.maxFileSizeBytes = maxFileSizeBytes; }
        public Long getMinFileSizeBytes() { return minFileSizeBytes; }
        public void setMinFileSizeBytes(Long minFileSizeBytes) { this.minFileSizeBytes = minFileSizeBytes; }
        public Integer getMinWidth() { return minWidth; }
        public void setMinWidth(Integer minWidth) { this.minWidth = minWidth; }
        public Integer getMinHeight() { return minHeight; }
        public void setMinHeight(Integer minHeight) { this.minHeight = minHeight; }
        public Integer getMaxWidth() { return maxWidth; }
        public void setMaxWidth(Integer maxWidth) { this.maxWidth = maxWidth; }
        public Integer getMaxHeight() { return maxHeight; }
        public void setMaxHeight(Integer maxHeight) { this.maxHeight = maxHeight; }
        public List<String> getAllowedMimeTypes() { return allowedMimeTypes; }
        public void setAllowedMimeTypes(List<String> allowedMimeTypes) { this.allowedMimeTypes = allowedMimeTypes; }
        public List<String> getDenyMimeTypes() { return denyMimeTypes; }
        public void setDenyMimeTypes(List<String> denyMimeTypes) { this.denyMimeTypes = denyMimeTypes; }
    }
}
