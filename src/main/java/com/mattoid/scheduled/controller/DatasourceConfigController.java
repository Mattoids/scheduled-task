package com.mattoid.scheduled.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mattoid.scheduled.audit.OperationAudit;
import com.mattoid.scheduled.common.PageResult;
import com.mattoid.scheduled.common.PageUtil;
import com.mattoid.scheduled.common.Result;
import com.mattoid.scheduled.common.TestConnectionResult;
import com.mattoid.scheduled.dto.PageQuery;
import com.mattoid.scheduled.entity.AiKnowledgeDoc;
import com.mattoid.scheduled.entity.DatasourceConfig;
import com.mattoid.scheduled.service.DatasourceConfigService;
import com.mattoid.scheduled.util.CryptoUtil;
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
    public Result<PageResult<DatasourceConfig>> page(PageQuery query,
                                                     @RequestParam(required = false) String name) {
        LambdaQueryWrapper<DatasourceConfig> wrapper = new LambdaQueryWrapper<DatasourceConfig>()
                .like(StringUtils.hasText(name), DatasourceConfig::getName, name)
                .orderByDesc(DatasourceConfig::getCreateTime);
        Page<DatasourceConfig> page = datasourceConfigService.page(new Page<>(query.getCurrent(), query.getSize()), wrapper);
        page.getRecords().forEach(this::decryptSensitive);
        return Result.ok(PageUtil.convert(page));
    }

    @PreAuthorize("hasAuthority('datasource:view')")
    @GetMapping("/{id}")
    public Result<DatasourceConfig> detail(@PathVariable Long id) {
        DatasourceConfig config = datasourceConfigService.getById(id);
        decryptSensitive(config);
        return Result.ok(config);
    }

    @OperationAudit(operationType = "CREATE", resourceType = "DATASOURCE")
    @PreAuthorize("hasAuthority('datasource:create')")
    @PostMapping
    public Result<Boolean> create(@RequestBody DatasourceConfig config) {
        return Result.ok(datasourceConfigService.saveOrUpdateDatasource(config));
    }

    @OperationAudit(operationType = "UPDATE", resourceType = "DATASOURCE")
    @PreAuthorize("hasAuthority('datasource:edit')")
    @PutMapping("/{id}")
    public Result<Boolean> update(@PathVariable Long id, @RequestBody DatasourceConfig config) {
        config.setId(id);
        return Result.ok(datasourceConfigService.saveOrUpdateDatasource(config));
    }

    @OperationAudit(operationType = "DELETE", resourceType = "DATASOURCE")
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

    @OperationAudit(operationType = "UPDATE", resourceType = "DATASOURCE")
    @PreAuthorize("hasAuthority('datasource:edit')")
    @PostMapping("/{id}/sync-schema")
    public Result<AiKnowledgeDoc> syncSchema(@PathVariable Long id) throws Exception {
        return Result.ok(datasourceConfigService.syncSchema(id));
    }

    private void decryptSensitive(DatasourceConfig config) {
        if (config == null) {
            return;
        }
        config.setPassword(CryptoUtil.decryptIfNeeded(config.getPassword()));
        config.setSshPassword(CryptoUtil.decryptIfNeeded(config.getSshPassword()));
        config.setSshPrivateKey(CryptoUtil.decryptIfNeeded(config.getSshPrivateKey()));
        config.setSshPassphrase(CryptoUtil.decryptIfNeeded(config.getSshPassphrase()));
        config.setSshAuthType(StringUtils.hasText(config.getSshPrivateKey()) ? "key" : "password");
    }

}
