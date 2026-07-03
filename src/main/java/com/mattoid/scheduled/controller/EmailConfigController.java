package com.mattoid.scheduled.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mattoid.scheduled.common.PageResult;
import com.mattoid.scheduled.common.PageUtil;
import com.mattoid.scheduled.common.Result;
import com.mattoid.scheduled.common.TestConnectionResult;
import com.mattoid.scheduled.dto.PageQuery;
import com.mattoid.scheduled.entity.EmailConfig;
import com.mattoid.scheduled.service.EmailConfigService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/email-config")
public class EmailConfigController {

    private final EmailConfigService emailConfigService;

    public EmailConfigController(EmailConfigService emailConfigService) {
        this.emailConfigService = emailConfigService;
    }

    @PreAuthorize("hasAuthority('email:view')")
    @GetMapping("/page")
    public Result<PageResult<EmailConfig>> page(PageQuery query) {
        Page<EmailConfig> page = emailConfigService.page(new Page<>(query.getCurrent(), query.getSize()));
        return Result.ok(PageUtil.convert(page));
    }

    @PreAuthorize("hasAuthority('email:view')")
    @GetMapping("/{id}")
    public Result<EmailConfig> detail(@PathVariable Long id) {
        return Result.ok(emailConfigService.getById(id));
    }

    @PreAuthorize("hasAuthority('email:create')")
    @PostMapping
    public Result<Boolean> create(@RequestBody EmailConfig config) {
        return Result.ok(emailConfigService.saveOrUpdate(config));
    }

    @PreAuthorize("hasAuthority('email:edit')")
    @PutMapping("/{id}")
    public Result<Boolean> update(@PathVariable Long id, @RequestBody EmailConfig config) {
        config.setId(id);
        return Result.ok(emailConfigService.saveOrUpdate(config));
    }

    @PreAuthorize("hasAuthority('email:view')")
    @PostMapping("/test")
    public Result<TestConnectionResult> test(@RequestBody EmailConfig config) {
        return Result.ok(emailConfigService.testConnection(config));
    }

    @PreAuthorize("hasAuthority('email:delete')")
    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.ok(emailConfigService.removeById(id));
    }
}
