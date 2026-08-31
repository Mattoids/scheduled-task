package com.mattoid.scheduled.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mattoid.scheduled.audit.OperationAudit;
import com.mattoid.scheduled.common.PageResult;
import com.mattoid.scheduled.common.PageUtil;
import com.mattoid.scheduled.common.Result;
import com.mattoid.scheduled.dto.PageQuery;
import com.mattoid.scheduled.dto.SqlPreviewResult;
import com.mattoid.scheduled.entity.ReportTemplate;
import com.mattoid.scheduled.entity.TaskSqlConfig;
import com.mattoid.scheduled.entity.TaskSqlGroup;
import com.mattoid.scheduled.service.ReportTemplateService;
import com.mattoid.scheduled.service.TaskSqlConfigService;
import com.mattoid.scheduled.service.TaskSqlGroupService;
import com.mattoid.scheduled.task.SqlExecutor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/task-sql")
public class TaskSqlConfigController {

    private final TaskSqlConfigService taskSqlConfigService;
    private final TaskSqlGroupService taskSqlGroupService;
    private final ReportTemplateService reportTemplateService;
    private final SqlExecutor sqlExecutor;

    public TaskSqlConfigController(TaskSqlConfigService taskSqlConfigService,
                                   TaskSqlGroupService taskSqlGroupService,
                                   ReportTemplateService reportTemplateService,
                                   SqlExecutor sqlExecutor) {
        this.taskSqlConfigService = taskSqlConfigService;
        this.taskSqlGroupService = taskSqlGroupService;
        this.reportTemplateService = reportTemplateService;
        this.sqlExecutor = sqlExecutor;
    }

    @PreAuthorize("hasAuthority('task:view')")
    @GetMapping("/page")
    public Result<PageResult<TaskSqlConfig>> page(PageQuery query,
                                                  @RequestParam(required = false) String sqlName,
                                                  @RequestParam(required = false) String sqlCode,
                                                  @RequestParam(required = false) String groupCode) {
        LambdaQueryWrapper<TaskSqlConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.hasText(sqlName), TaskSqlConfig::getSqlName, sqlName)
                .like(StringUtils.hasText(sqlCode), TaskSqlConfig::getSqlCode, sqlCode)
                .eq(StringUtils.hasText(groupCode), TaskSqlConfig::getGroupCode, groupCode)
                .orderByDesc(TaskSqlConfig::getCreateTime);
        Page<TaskSqlConfig> page = taskSqlConfigService.page(new Page<>(query.getCurrent(), query.getSize()), wrapper);
        populateGroupNames(page.getRecords());
        return Result.ok(PageUtil.convert(page));
    }

    @PreAuthorize("hasAuthority('task:view')")
    @GetMapping("/list")
    public Result<List<TaskSqlConfig>> list() {
        List<TaskSqlConfig> configs = taskSqlConfigService.lambdaQuery()
                .eq(TaskSqlConfig::getStatus, 1)
                .list();
        populateGroupNames(configs);
        return Result.ok(configs);
    }

    @PreAuthorize("hasAuthority('task:view')")
    @GetMapping("/{id}")
    public Result<TaskSqlConfig> detail(@PathVariable Long id) {
        TaskSqlConfig config = taskSqlConfigService.getById(id);
        if (config != null) {
            if (config.getGroupId() != null && !StringUtils.hasText(config.getGroupCode())) {
                TaskSqlGroup group = taskSqlGroupService.getById(config.getGroupId());
                config.setTaskSqlGroup(group);
                config.setGroupName(group != null ? group.getGroupName() : null);
                config.setGroupCode(group != null ? group.getGroupCode() : null);
            } else if (StringUtils.hasText(config.getGroupCode())) {
                TaskSqlGroup group = taskSqlGroupService.getByCode(config.getGroupCode());
                config.setTaskSqlGroup(group);
                config.setGroupName(group != null ? group.getGroupName() : null);
            }
            if (config.getTemplateId() != null && !StringUtils.hasText(config.getTemplateCode())) {
                ReportTemplate template = reportTemplateService.getById(config.getTemplateId());
                config.setTemplateCode(template != null ? template.getTemplateCode() : null);
            }
        }
        return Result.ok(config);
    }

    @PreAuthorize("hasAuthority('task:view')")
    @PostMapping("/preview")
    public Result<SqlPreviewResult> preview(@RequestBody TaskSqlConfig config) {
        String sqlContent = config.getSqlContent();
        if (!StringUtils.hasText(sqlContent)) {
            throw new IllegalArgumentException("SQL 内容为空");
        }
        Map<String, Object> params = parseCustomParams(config.getCustomParams());
        String previewSql = sqlExecutor.previewSql(sqlContent, params);
        SqlPreviewResult result = new SqlPreviewResult();
        result.setSql(previewSql);
        return Result.ok(result);
    }

    @OperationAudit(operationType = "CREATE", resourceType = "SQL_CONFIG")
    @PreAuthorize("hasAuthority('task:create')")
    @PostMapping
    public Result<Boolean> create(@RequestBody TaskSqlConfig config) {
        resolveTemplateAndGroup(config);
        return Result.ok(taskSqlConfigService.save(config));
    }

    @OperationAudit(operationType = "UPDATE", resourceType = "SQL_CONFIG")
    @PreAuthorize("hasAuthority('task:edit')")
    @PutMapping("/{id}")
    public Result<Boolean> update(@PathVariable Long id, @RequestBody TaskSqlConfig config) {
        config.setId(id);
        resolveTemplateAndGroup(config);
        return Result.ok(taskSqlConfigService.updateById(config));
    }

    @OperationAudit(operationType = "DELETE", resourceType = "SQL_CONFIG")
    @PreAuthorize("hasAuthority('task:delete')")
    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        return Result.ok(taskSqlConfigService.removeSqlConfig(id));
    }

    private void resolveTemplateAndGroup(TaskSqlConfig config) {
        if (StringUtils.hasText(config.getTemplateCode())) {
            ReportTemplate template = reportTemplateService.getByCode(config.getTemplateCode());
            if (template == null) {
                throw new IllegalArgumentException("报表模板编码不存在: " + config.getTemplateCode());
            }
            config.setTemplateId(template.getId());
        } else {
            config.setTemplateId(null);
        }
        if (StringUtils.hasText(config.getGroupCode())) {
            TaskSqlGroup group = taskSqlGroupService.getByCode(config.getGroupCode());
            if (group != null) {
                config.setGroupId(group.getId());
            }
        } else {
            config.setGroupId(null);
        }
    }

    private void populateGroupNames(List<TaskSqlConfig> configs) {
        if (configs == null || configs.isEmpty()) {
            return;
        }
        List<String> groupCodes = configs.stream()
                .map(TaskSqlConfig::getGroupCode)
                .distinct()
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
        if (groupCodes.isEmpty()) {
            return;
        }
        Map<String, TaskSqlGroup> groupMap = taskSqlGroupService.lambdaQuery()
                .in(TaskSqlGroup::getGroupCode, groupCodes)
                .list().stream()
                .collect(Collectors.toMap(TaskSqlGroup::getGroupCode, g -> g));
        for (TaskSqlConfig config : configs) {
            TaskSqlGroup group = groupMap.get(config.getGroupCode());
            if (group != null) {
                config.setTaskSqlGroup(group);
                config.setGroupName(group.getGroupName());
            }
        }
    }

    private Map<String, Object> parseCustomParams(String customParams) {
        if (!StringUtils.hasText(customParams)) {
            return Collections.emptyMap();
        }
        try {
            return new ObjectMapper().readValue(customParams, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("自定义参数必须是合法的 JSON 对象: " + e.getMessage());
        }
    }
}
