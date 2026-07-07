package com.mattoid.scheduled.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mattoid.scheduled.audit.OperationAudit;
import com.mattoid.scheduled.common.PageResult;
import com.mattoid.scheduled.common.PageUtil;
import com.mattoid.scheduled.common.Result;
import com.mattoid.scheduled.dto.PageQuery;
import com.mattoid.scheduled.entity.ReportTemplate;
import com.mattoid.scheduled.entity.TaskWebCrawlConfig;
import com.mattoid.scheduled.entity.TaskWebCrawlSelector;
import com.mattoid.scheduled.service.ReportTemplateService;
import com.mattoid.scheduled.service.TaskWebCrawlConfigService;
import com.mattoid.scheduled.service.TaskWebCrawlSelectorService;
import com.mattoid.scheduled.task.WebCrawlExecutor;
import com.mattoid.scheduled.task.WebCrawlPreviewResult;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

@RestController
@RequestMapping("/api/task-crawl")
public class TaskWebCrawlConfigController {

    private final TaskWebCrawlConfigService taskWebCrawlConfigService;
    private final TaskWebCrawlSelectorService taskWebCrawlSelectorService;
    private final ReportTemplateService reportTemplateService;
    private final WebCrawlExecutor webCrawlExecutor;

    public TaskWebCrawlConfigController(TaskWebCrawlConfigService taskWebCrawlConfigService,
                                        TaskWebCrawlSelectorService taskWebCrawlSelectorService,
                                        ReportTemplateService reportTemplateService,
                                        WebCrawlExecutor webCrawlExecutor) {
        this.taskWebCrawlConfigService = taskWebCrawlConfigService;
        this.taskWebCrawlSelectorService = taskWebCrawlSelectorService;
        this.reportTemplateService = reportTemplateService;
        this.webCrawlExecutor = webCrawlExecutor;
    }

    @PreAuthorize("hasAuthority('taskCrawl:view')")
    @GetMapping("/page")
    public Result<PageResult<TaskWebCrawlConfig>> page(PageQuery query,
                                                          @RequestParam(required = false) String crawlName,
                                                          @RequestParam(required = false) String crawlCode) {
        LambdaQueryWrapper<TaskWebCrawlConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(crawlName), TaskWebCrawlConfig::getCrawlName, crawlName)
                .like(StringUtils.hasText(crawlCode), TaskWebCrawlConfig::getCrawlCode, crawlCode)
                .orderByDesc(TaskWebCrawlConfig::getCreateTime);
        Page<TaskWebCrawlConfig> page = taskWebCrawlConfigService.page(new Page<>(query.getCurrent(), query.getSize()), wrapper);
        return Result.ok(PageUtil.convert(page));
    }

    @PreAuthorize("hasAuthority('taskCrawl:view')")
    @GetMapping("/list")
    public Result<List<TaskWebCrawlConfig>> list() {
        return Result.ok(taskWebCrawlConfigService.lambdaQuery()
                .eq(TaskWebCrawlConfig::getStatus, 1)
                .list());
    }

    @PreAuthorize("hasAuthority('taskCrawl:view')")
    @PostMapping("/preview")
    public Result<WebCrawlPreviewResult> preview(@RequestBody TaskWebCrawlConfig config) {
        return Result.ok(webCrawlExecutor.preview(config, null));
    }

    @PreAuthorize("hasAuthority('taskCrawl:view')")
    @GetMapping("/{id}")
    public Result<TaskWebCrawlConfig> detail(@PathVariable Long id) {
        TaskWebCrawlConfig config = taskWebCrawlConfigService.getDecryptedById(id);
        if (config != null) {
            if (config.getTemplateId() != null && !StringUtils.hasText(config.getTemplateCode())) {
                ReportTemplate template = reportTemplateService.getById(config.getTemplateId());
                config.setTemplateCode(template != null ? template.getTemplateCode() : null);
            }
            taskWebCrawlConfigService.populateSelectors(Collections.singletonList(config));
        }
        return Result.ok(config);
    }

    @OperationAudit(operationType = "CREATE", resourceType = "CRAWL_CONFIG")
    @PreAuthorize("hasAuthority('taskCrawl:create')")
    @PostMapping
    public Result<Boolean> create(@RequestBody TaskWebCrawlConfig config) {
        resolveTemplate(config);
        boolean saved = taskWebCrawlConfigService.save(config);
        saveSelectors(config);
        return Result.ok(saved);
    }

    @OperationAudit(operationType = "UPDATE", resourceType = "CRAWL_CONFIG")
    @PreAuthorize("hasAuthority('taskCrawl:edit')")
    @PutMapping("/{id}")
    public Result<Boolean> update(@PathVariable Long id, @RequestBody TaskWebCrawlConfig config) {
        config.setId(id);
        resolveTemplate(config);
        boolean updated = taskWebCrawlConfigService.updateById(config);
        saveSelectors(config);
        return Result.ok(updated);
    }

    @OperationAudit(operationType = "DELETE", resourceType = "CRAWL_CONFIG")
    @PreAuthorize("hasAuthority('taskCrawl:delete')")
    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.ok(taskWebCrawlConfigService.removeCrawlConfig(id));
    }

    private void resolveTemplate(TaskWebCrawlConfig config) {
        if (StringUtils.hasText(config.getTemplateCode())) {
            ReportTemplate template = reportTemplateService.getByCode(config.getTemplateCode());
            if (template == null) {
                throw new IllegalArgumentException("报表模板编码不存在: " + config.getTemplateCode());
            }
            config.setTemplateId(template.getId());
        } else {
            config.setTemplateId(null);
        }
    }

    private void saveSelectors(TaskWebCrawlConfig config) {
        List<TaskWebCrawlSelector> selectors = config.getSelectors();
        if (selectors == null) {
            return;
        }
        Long configId = config.getId();
        if (configId == null) {
            return;
        }
        taskWebCrawlSelectorService.lambdaUpdate()
                .eq(TaskWebCrawlSelector::getCrawlConfigId, configId)
                .remove();
        if (CollectionUtils.isEmpty(selectors)) {
            return;
        }
        int sort = 0;
        for (TaskWebCrawlSelector selector : selectors) {
            selector.setId(null);
            selector.setCrawlConfigId(configId);
            selector.setCrawlCode(config.getCrawlCode());
            selector.setSortOrder(sort++);
        }
        taskWebCrawlSelectorService.saveBatch(selectors);
    }
}
