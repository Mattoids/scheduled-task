package com.mattoid.scheduled.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mattoid.scheduled.common.PageResult;
import com.mattoid.scheduled.common.PageUtil;
import com.mattoid.scheduled.common.Result;
import com.mattoid.scheduled.dto.PageQuery;
import com.mattoid.scheduled.entity.ReportTemplate;
import com.mattoid.scheduled.service.ReportTemplateService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/template")
public class ReportTemplateController {

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

    @PreAuthorize("hasAuthority('template:delete')")
    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.ok(reportTemplateService.removeById(id));
    }
}
