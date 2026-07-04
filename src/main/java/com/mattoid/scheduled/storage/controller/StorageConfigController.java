package com.mattoid.scheduled.storage.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mattoid.scheduled.common.PageResult;
import com.mattoid.scheduled.common.PageUtil;
import com.mattoid.scheduled.common.Result;
import com.mattoid.scheduled.dto.PageQuery;
import com.mattoid.scheduled.entity.StorageConfig;
import com.mattoid.scheduled.storage.service.StorageConfigService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/storage-config")
public class StorageConfigController {

    private final StorageConfigService storageConfigService;

    public StorageConfigController(StorageConfigService storageConfigService) {
        this.storageConfigService = storageConfigService;
    }

    @PreAuthorize("hasAuthority('storageConfig:view')")
    @GetMapping("/page")
    public Result<PageResult<StorageConfig>> page(PageQuery query,
                                                     @RequestParam(required = false) String configName,
                                                     @RequestParam(required = false) String storageType) {
        LambdaQueryWrapper<StorageConfig> wrapper = new LambdaQueryWrapper<StorageConfig>()
                .like(StringUtils.hasText(configName), StorageConfig::getConfigName, configName)
                .eq(StringUtils.hasText(storageType), StorageConfig::getStorageType, storageType)
                .orderByDesc(StorageConfig::getCreateTime);
        Page<StorageConfig> page = storageConfigService.page(new Page<>(query.getCurrent(), query.getSize()), wrapper);
        return Result.ok(PageUtil.convert(page));
    }

    @PreAuthorize("hasAuthority('storageConfig:view')")
    @GetMapping("/list")
    public Result<List<StorageConfig>> list() {
        return Result.ok(storageConfigService.lambdaQuery().eq(StorageConfig::getStatus, 1).list());
    }

    @PreAuthorize("hasAuthority('storageConfig:view')")
    @GetMapping("/{id}")
    public Result<StorageConfig> detail(@PathVariable Long id) {
        return Result.ok(storageConfigService.getById(id));
    }

    @PreAuthorize("hasAuthority('storageConfig:create')")
    @PostMapping
    public Result<Boolean> create(@RequestBody StorageConfig config) {
        return Result.ok(storageConfigService.saveOrUpdateConfig(config));
    }

    @PreAuthorize("hasAuthority('storageConfig:edit')")
    @PutMapping("/{id}")
    public Result<Boolean> update(@PathVariable Long id, @RequestBody StorageConfig config) {
        config.setId(id);
        return Result.ok(storageConfigService.saveOrUpdateConfig(config));
    }

    @PreAuthorize("hasAuthority('storageConfig:delete')")
    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.ok(storageConfigService.removeById(id));
    }

    @PreAuthorize("hasAuthority('storageConfig:edit')")
    @PostMapping("/{id}/test")
    public Result<String> test(@PathVariable Long id) {
        String result = storageConfigService.testConfig(id);
        if (result.startsWith("测试成功")) {
            return Result.ok(result);
        }
        return Result.error(result);
    }
}
