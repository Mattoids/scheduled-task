package com.mattoid.scheduled.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mattoid.scheduled.entity.ReportTemplate;
import com.mattoid.scheduled.mapper.ReportTemplateMapper;
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

    @Value("${report.upload.path}")
    private String uploadPath;

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
