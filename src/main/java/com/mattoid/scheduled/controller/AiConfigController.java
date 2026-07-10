package com.mattoid.scheduled.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mattoid.scheduled.ai.AiChatResponse;
import com.mattoid.scheduled.common.Result;
import com.mattoid.scheduled.common.PageResult;
import com.mattoid.scheduled.common.PageUtil;
import com.mattoid.scheduled.dto.ChangeDefaultRequest;
import com.mattoid.scheduled.dto.PageQuery;
import com.mattoid.scheduled.entity.AiConfig;
import com.mattoid.scheduled.service.AiConfigService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai-config")
public class AiConfigController {

    private final AiConfigService aiConfigService;

    public AiConfigController(AiConfigService aiConfigService) {
        this.aiConfigService = aiConfigService;
    }

    @PreAuthorize("hasAuthority('system:user')")
    @GetMapping("/page")
    public Result<PageResult<AiConfig>> page(PageQuery query,
                                             @RequestParam(required = false) String configName,
                                             @RequestParam(required = false) String provider) {
        LambdaQueryWrapper<AiConfig> wrapper = new LambdaQueryWrapper<AiConfig>()
                .like(StringUtils.hasText(configName), AiConfig::getConfigName, configName)
                .eq(StringUtils.hasText(provider), AiConfig::getProvider, provider)
                .orderByDesc(AiConfig::getCreateTime);
        Page<AiConfig> page = aiConfigService.page(new Page<>(query.getCurrent(), query.getSize()), wrapper);
        return Result.ok(PageUtil.convert(page));
    }

    @PreAuthorize("hasAuthority('system:user')")
    @GetMapping("/list")
    public Result<java.util.List<AiConfig>> list() {
        return Result.ok(aiConfigService.lambdaQuery().eq(AiConfig::getStatus, 1).list());
    }

    @PreAuthorize("hasAuthority('system:user')")
    @GetMapping("/{id}")
    public Result<AiConfig> detail(@PathVariable Long id) {
        return Result.ok(aiConfigService.getById(id));
    }

    @PreAuthorize("hasAuthority('system:user')")
    @PostMapping
    public Result<Boolean> create(@RequestBody AiConfig config) {
        return Result.ok(aiConfigService.saveOrUpdateConfig(config));
    }

    @PreAuthorize("hasAuthority('system:user')")
    @PutMapping("/{id}")
    public Result<Boolean> update(@PathVariable Long id, @RequestBody AiConfig config) {
        config.setId(id);
        return Result.ok(aiConfigService.saveOrUpdateConfig(config));
    }

    @PreAuthorize("hasAuthority('system:user')")
    @PutMapping("/{id}/default")
    public Result<Boolean> updateDefault(@PathVariable Long id, @RequestBody ChangeDefaultRequest request) {
        return Result.ok(aiConfigService.updateDefault(id, request.getIsDefault()));
    }

    @PreAuthorize("hasAuthority('system:user')")
    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.ok(aiConfigService.removeById(id));
    }

    @PreAuthorize("hasAuthority('system:user')")
    @PostMapping("/{id}/test")
    public Result<String> test(@PathVariable Long id) {
        AiChatResponse response = aiConfigService.testConfig(id);
        if (response.isSuccess()) {
            return Result.ok(response.getContent());
        }
        return Result.error(response.getErrorMessage());
    }

    @PreAuthorize("hasAuthority('system:user')")
    @PostMapping("/test")
    public Result<String> testConfig(@RequestBody AiConfig config) {
        AiChatResponse response = aiConfigService.testConfig(config);
        if (response.isSuccess()) {
            return Result.ok(response.getContent());
        }
        return Result.error(response.getErrorMessage());
    }
}
