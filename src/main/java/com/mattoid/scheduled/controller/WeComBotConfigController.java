package com.mattoid.scheduled.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mattoid.scheduled.common.PageResult;
import com.mattoid.scheduled.common.PageUtil;
import com.mattoid.scheduled.common.Result;
import com.mattoid.scheduled.common.TestConnectionResult;
import com.mattoid.scheduled.dto.PageQuery;
import com.mattoid.scheduled.entity.WeComBotConfig;
import com.mattoid.scheduled.service.WeComBotConfigService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/wecom-bot-config")
public class WeComBotConfigController {

    private final WeComBotConfigService weComBotConfigService;

    public WeComBotConfigController(WeComBotConfigService weComBotConfigService) {
        this.weComBotConfigService = weComBotConfigService;
    }

    @PreAuthorize("hasAuthority('wecomBot:view')")
    @GetMapping("/page")
    public Result<PageResult<WeComBotConfig>> page(PageQuery query) {
        Page<WeComBotConfig> page = weComBotConfigService.page(new Page<>(query.getCurrent(), query.getSize()));
        return Result.ok(PageUtil.convert(page));
    }

    @PreAuthorize("hasAuthority('wecomBot:view')")
    @GetMapping("/{id}")
    public Result<WeComBotConfig> detail(@PathVariable Long id) {
        return Result.ok(weComBotConfigService.getById(id));
    }

    @PreAuthorize("hasAuthority('wecomBot:create')")
    @PostMapping
    public Result<Boolean> create(@RequestBody WeComBotConfig config) {
        return Result.ok(weComBotConfigService.saveOrUpdate(config));
    }

    @PreAuthorize("hasAuthority('wecomBot:edit')")
    @PutMapping("/{id}")
    public Result<Boolean> update(@PathVariable Long id, @RequestBody WeComBotConfig config) {
        config.setId(id);
        return Result.ok(weComBotConfigService.saveOrUpdate(config));
    }

    @PreAuthorize("hasAuthority('wecomBot:view')")
    @PostMapping("/test")
    public Result<TestConnectionResult> test(@RequestBody WeComBotConfig config) {
        return Result.ok(weComBotConfigService.testConnection(config));
    }

    @PreAuthorize("hasAuthority('wecomBot:delete')")
    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.ok(weComBotConfigService.removeById(id));
    }
}
