package com.mattoid.scheduled.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mattoid.scheduled.common.PageResult;
import com.mattoid.scheduled.common.PageUtil;
import com.mattoid.scheduled.common.Result;
import com.mattoid.scheduled.common.TestConnectionResult;
import com.mattoid.scheduled.dto.PageQuery;
import com.mattoid.scheduled.entity.DatasourceConfig;
import com.mattoid.scheduled.service.DatasourceConfigService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/datasource")
public class DatasourceConfigController {

    private final DatasourceConfigService datasourceConfigService;

    public DatasourceConfigController(DatasourceConfigService datasourceConfigService) {
        this.datasourceConfigService = datasourceConfigService;
    }

    @PreAuthorize("hasAuthority('datasource:view')")
    @GetMapping("/page")
    public Result<PageResult<DatasourceConfig>> page(PageQuery query) {
        Page<DatasourceConfig> page = datasourceConfigService.page(new Page<>(query.getCurrent(), query.getSize()));
        page.getRecords().forEach(this::maskSensitive);
        return Result.ok(PageUtil.convert(page));
    }

    @PreAuthorize("hasAuthority('datasource:view')")
    @GetMapping("/{id}")
    public Result<DatasourceConfig> detail(@PathVariable Long id) {
        DatasourceConfig config = datasourceConfigService.getById(id);
        maskSensitive(config);
        return Result.ok(config);
    }

    @PreAuthorize("hasAuthority('datasource:create')")
    @PostMapping
    public Result<Boolean> create(@RequestBody DatasourceConfig config) {
        return Result.ok(datasourceConfigService.saveOrUpdateDatasource(config));
    }

    @PreAuthorize("hasAuthority('datasource:edit')")
    @PutMapping("/{id}")
    public Result<Boolean> update(@PathVariable Long id, @RequestBody DatasourceConfig config) {
        config.setId(id);
        return Result.ok(datasourceConfigService.saveOrUpdateDatasource(config));
    }

    @PreAuthorize("hasAuthority('datasource:delete')")
    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.ok(datasourceConfigService.removeDatasource(id));
    }

    @PreAuthorize("hasAuthority('datasource:view')")
    @PostMapping("/test")
    public Result<TestConnectionResult> test(@RequestBody DatasourceConfig config) {
        return Result.ok(datasourceConfigService.testConnection(config));
    }

    @PreAuthorize("hasAuthority('datasource:edit')")
    @PostMapping("/{id}/test")
    public Result<TestConnectionResult> test(@PathVariable Long id) {
        return Result.ok(datasourceConfigService.testConnection(id));
    }

    private void maskSensitive(DatasourceConfig config) {
        if (config == null) {
            return;
        }
        config.setSshAuthType(StringUtils.hasText(config.getSshPrivateKey()) ? "key" : "password");
        config.setSshPassword(null);
        config.setSshPrivateKey(null);
        config.setSshPassphrase(null);
    }

}
