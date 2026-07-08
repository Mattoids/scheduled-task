package com.mattoid.scheduled.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mattoid.scheduled.common.PageResult;
import com.mattoid.scheduled.common.PageUtil;
import com.mattoid.scheduled.common.Result;
import com.mattoid.scheduled.dto.PageQuery;
import com.mattoid.scheduled.entity.ReportTemplate;
import com.mattoid.scheduled.service.ReportTemplateService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/template")
public class ReportTemplateController {

    @Value("${report.upload.path}")
    private String uploadPath;

    private final ReportTemplateService reportTemplateService;

    public ReportTemplateController(ReportTemplateService reportTemplateService) {
        this.reportTemplateService = reportTemplateService;
    }

    @PreAuthorize("hasAuthority('template:view')")
    @GetMapping("/page")
    public Result<PageResult<ReportTemplate>> page(PageQuery query,
                                                   @RequestParam(required = false) String templateName) {
        LambdaQueryWrapper<ReportTemplate> wrapper = new LambdaQueryWrapper<ReportTemplate>()
                .like(StringUtils.hasText(templateName), ReportTemplate::getTemplateName, templateName)
                .orderByDesc(ReportTemplate::getCreateTime);
        Page<ReportTemplate> page = reportTemplateService.page(new Page<>(query.getCurrent(), query.getSize()), wrapper);
        return Result.ok(PageUtil.convert(page));
    }

    @PreAuthorize("hasAuthority('template:view')")
    @GetMapping("/{id}")
    public Result<ReportTemplate> detail(@PathVariable Long id) {
        return Result.ok(reportTemplateService.getById(id));
    }

    @PreAuthorize("hasAuthority('template:create')")
    @PostMapping("/upload")
    public Result<ReportTemplate> upload(@RequestParam("file") MultipartFile file,
                                           @RequestParam String templateName,
                                           @RequestParam String templateCode,
                                           @RequestParam(required = false) String description) throws Exception {
        return Result.ok(reportTemplateService.uploadTemplate(file, templateName, templateCode, description));
    }

    @PreAuthorize("hasAuthority('template:edit')")
    @PutMapping("/{id}")
    public Result<Boolean> update(@PathVariable Long id, @RequestBody ReportTemplate template) {
        template.setId(id);
        return Result.ok(reportTemplateService.updateTemplate(template));
    }

    @PreAuthorize("hasAuthority('template:delete')")
    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.ok(reportTemplateService.deleteTemplate(id));
    }

    @PreAuthorize("hasAuthority('template:view')")
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        ReportTemplate template = reportTemplateService.getById(id);
        if (template == null) {
            return ResponseEntity.notFound().build();
        }
        File file = resolveTemplateFile(template.getFilePath());
        Path basePath = Paths.get(uploadPath).normalize();
        Path filePath = file.toPath().normalize();
        if (!filePath.startsWith(basePath) || !file.exists() || !file.isFile()) {
            return ResponseEntity.notFound().build();
        }
        Resource resource = new PathResource(file.toPath());
        String fileName = StringUtils.hasText(template.getFileName()) ? template.getFileName() : file.getName();
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(fileName, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
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
}
