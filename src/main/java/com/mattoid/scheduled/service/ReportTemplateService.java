package com.mattoid.scheduled.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mattoid.scheduled.entity.ReportTemplate;
import com.mattoid.scheduled.mapper.ReportTemplateMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class ReportTemplateService extends ServiceImpl<ReportTemplateMapper, ReportTemplate> {

    private static final Logger log = LoggerFactory.getLogger(ReportTemplateService.class);

    @Value("${report.upload.path}")
    private String uploadPath;

    public ReportTemplate getByCode(String templateCode) {
        if (!StringUtils.hasText(templateCode)) {
            return null;
        }
        return lambdaQuery().eq(ReportTemplate::getTemplateCode, templateCode).one();
    }

    public ReportTemplate uploadTemplate(MultipartFile file, String templateName, String templateCode, String description) throws IOException {
        Path dir = Paths.get(uploadPath, "templates");
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }

        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf("."));
        }
        String fileName = UUID.randomUUID() + ext;
        Path filePath = dir.resolve(fileName);
        file.transferTo(filePath);

        ReportTemplate template = new ReportTemplate();
        template.setTemplateName(templateName);
        template.setTemplateCode(templateCode);
        template.setTemplateType(detectType(ext));
        template.setFilePath("templates/" + fileName);
        template.setFileName(originalName);
        template.setDescription(description);
        template.setStatus(1);
        save(template);
        return template;
    }

    public boolean updateTemplate(ReportTemplate template) {
        ReportTemplate existing = getById(template.getId());
        if (existing == null) {
            throw new IllegalArgumentException("模板不存在");
        }
        existing.setTemplateName(template.getTemplateName());
        existing.setDescription(template.getDescription());
        return updateById(existing);
    }

    public boolean deleteTemplate(Long id) {
        ReportTemplate existing = getById(id);
        if (existing == null) {
            throw new IllegalArgumentException("模板不存在");
        }
        boolean removed = removeById(id);
        if (removed && StringUtils.hasText(existing.getFilePath())) {
            try {
                File file = resolveTemplateFile(existing.getFilePath());
                if (file.exists() && file.isFile()) {
                    file.delete();
                }
            } catch (Exception e) {
                // 文件删除失败不影响接口返回，仅记录
                log.warn("删除模板文件失败: {}", existing.getFilePath(), e);
            }
        }
        return removed;
    }

    private File resolveTemplateFile(String filePath) {
        if (!StringUtils.hasText(filePath)) {
            throw new IllegalArgumentException("模板文件路径为空");
        }
        Path path = Paths.get(filePath);
        if (path.isAbsolute()) {
            return path.toFile();
        }
        return Paths.get(uploadPath, filePath).toFile();
    }

    private String detectType(String ext) {
        String e = ext.toLowerCase();
        return switch (e) {
            case ".xls", ".xlsx" -> "EXCEL";
            case ".doc", ".docx" -> "WORD";
            case ".ppt", ".pptx" -> "PPT";
            case ".csv" -> "CSV";
            case ".txt" -> "TXT";
            default -> "UNKNOWN";
        };
    }
}
