package com.mattoid.scheduled.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mattoid.scheduled.common.PageResult;
import com.mattoid.scheduled.common.PageUtil;
import com.mattoid.scheduled.common.Result;
import com.mattoid.scheduled.common.TestConnectionResult;
import com.mattoid.scheduled.dto.PageQuery;
import com.mattoid.scheduled.entity.WeComAppConfig;
import com.mattoid.scheduled.service.WeComAppConfigService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/wecom-app-config")
public class WeComAppConfigController {

    private final WeComAppConfigService weComAppConfigService;

    public WeComAppConfigController(WeComAppConfigService weComAppConfigService) {
        this.weComAppConfigService = weComAppConfigService;
    }

    @PreAuthorize("hasAuthority('wecomApp:view')")
    @GetMapping("/page")
    public Result<PageResult<WeComAppConfig>> page(PageQuery query) {
        Page<WeComAppConfig> page = weComAppConfigService.page(new Page<>(query.getCurrent(), query.getSize()));
        return Result.ok(PageUtil.convert(page));
    }

    @PreAuthorize("hasAuthority('wecomApp:view')")
    @GetMapping("/{id}")
    public Result<WeComAppConfig> detail(@PathVariable Long id) {
        return Result.ok(weComAppConfigService.getById(id));
    }

    @PreAuthorize("hasAuthority('wecomApp:create')")
    @PostMapping
    public Result<Boolean> create(@RequestBody WeComAppConfig config) {
        return Result.ok(weComAppConfigService.saveOrUpdate(config));
    }

    @PreAuthorize("hasAuthority('wecomApp:edit')")
    @PutMapping("/{id}")
    public Result<Boolean> update(@PathVariable Long id, @RequestBody WeComAppConfig config) {
        config.setId(id);
        return Result.ok(weComAppConfigService.saveOrUpdate(config));
    }

    @PreAuthorize("hasAuthority('wecomApp:view')")
    @PostMapping("/test")
    public Result<TestConnectionResult> test(@RequestBody WeComAppConfig config) {
        return Result.ok(weComAppConfigService.testConnection(config));
    }

    @PreAuthorize("hasAuthority('wecomApp:delete')")
    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.ok(weComAppConfigService.removeById(id));
    }
}
