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
import com.mattoid.scheduled.entity.DatasourceSchemaSyncLog;
import com.mattoid.scheduled.service.AiKnowledgeDocService;
import com.mattoid.scheduled.service.DatasourceConfigService;
import com.mattoid.scheduled.service.DatasourceSchemaSyncLogService;
import com.mattoid.scheduled.util.CryptoUtil;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/datasource")
public class DatasourceConfigController {

    private final DatasourceConfigService datasourceConfigService;
    private final DatasourceSchemaSyncLogService datasourceSchemaSyncLogService;
    private final AiKnowledgeDocService aiKnowledgeDocService;

    public DatasourceConfigController(DatasourceConfigService datasourceConfigService,
                                      DatasourceSchemaSyncLogService datasourceSchemaSyncLogService,
                                      AiKnowledgeDocService aiKnowledgeDocService) {
        this.datasourceConfigService = datasourceConfigService;
        this.datasourceSchemaSyncLogService = datasourceSchemaSyncLogService;
        this.aiKnowledgeDocService = aiKnowledgeDocService;
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
        return Result.ok(datasourceSchemaSyncLogService.syncSchemaTracked(id));
    }

    @PreAuthorize("hasAuthority('datasource:view')")
    @GetMapping("/{id}/sync-logs")
    public Result<PageResult<DatasourceSchemaSyncLog>> syncLogs(@PathVariable Long id, PageQuery query) {
        Page<DatasourceSchemaSyncLog> page = datasourceSchemaSyncLogService.pageByDatasource(id, query.getCurrent(), query.getSize());
        return Result.ok(PageUtil.convert(page));
    }

    @PreAuthorize("hasAuthority('datasource:view')")
    @GetMapping("/{id}/sync-logs/{logId}")
    public Result<DatasourceSchemaSyncLog> syncLogDetail(@PathVariable Long id, @PathVariable Long logId) {
        DatasourceSchemaSyncLog log = datasourceSchemaSyncLogService.getById(logId);
        if (log == null || !id.equals(log.getDatasourceId())) {
            return Result.error("同步记录不存在");
        }
        return Result.ok(log);
    }

    @PreAuthorize("hasAuthority('datasource:view')")
    @GetMapping("/{id}/schema-docs/{docId}/content")
    public Result<String> schemaDocContent(@PathVariable Long id, @PathVariable Long docId) {
        AiKnowledgeDoc doc = aiKnowledgeDocService.getById(docId);
        if (doc == null || !id.equals(doc.getDatasourceId())) {
            return Result.error("数据字典文档不存在");
        }
        return Result.ok(aiKnowledgeDocService.readContent(doc));
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
