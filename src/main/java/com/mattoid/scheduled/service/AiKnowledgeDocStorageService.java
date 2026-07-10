package com.mattoid.scheduled.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
public class AiKnowledgeDocStorageService {

    @Value("${report.ai.knowledge-doc.path:${user.home}/scheduled-task/ai-knowledge-docs}")
    private String storagePath;

    private Path baseDir;

    @PostConstruct
    public void init() throws IOException {
        this.baseDir = Paths.get(storagePath);
        if (!Files.exists(baseDir)) {
            Files.createDirectories(baseDir);
        }
    }

    public String save(Long datasourceId, String docType, String content) throws IOException {
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("文档内容不能为空");
        }
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String fileName = String.format("%s_%s_%s_%s.md", datasourceId, docType, date, UUID.randomUUID().toString().substring(0, 8));
        Path dir = baseDir.resolve(date);
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
        Path file = dir.resolve(fileName);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file.toAbsolutePath().toString();
    }

    public String read(String filePath) {
        if (!StringUtils.hasText(filePath)) {
            return null;
        }
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                log.warn("知识文档文件不存在: {}", filePath);
                return null;
            }
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("读取知识文档文件失败: {}", filePath, e);
            return null;
        }
    }

    /** 覆盖写入指定路径，用于同一数据源重复同步时更新其唯一的数据字典文件。 */
    public void writeToPath(String filePath, String content) throws IOException {
        if (!StringUtils.hasText(filePath)) {
            throw new IllegalArgumentException("文档路径不能为空");
        }
        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("文档内容不能为空");
        }
        Path path = Paths.get(filePath);
        if (path.getParent() != null && !Files.exists(path.getParent())) {
            Files.createDirectories(path.getParent());
        }
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
