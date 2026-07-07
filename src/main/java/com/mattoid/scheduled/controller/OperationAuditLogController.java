package com.mattoid.scheduled.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mattoid.scheduled.common.PageResult;
import com.mattoid.scheduled.common.PageUtil;
import com.mattoid.scheduled.common.Result;
import com.mattoid.scheduled.dto.PageQuery;
import com.mattoid.scheduled.entity.OperationAuditLog;
import com.mattoid.scheduled.service.OperationAuditLogService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit-log")
public class OperationAuditLogController {

    private final OperationAuditLogService operationAuditLogService;

    public OperationAuditLogController(OperationAuditLogService operationAuditLogService) {
        this.operationAuditLogService = operationAuditLogService;
    }

    @PreAuthorize("hasAuthority('auditLog:view')")
    @GetMapping("/page")
    public Result<PageResult<OperationAuditLog>> page(PageQuery query,
                                                        @RequestParam(required = false) String operator,
                                                        @RequestParam(required = false) String operationType,
                                                        @RequestParam(required = false) String resourceType,
                                                        @RequestParam(required = false) String status) {
        LambdaQueryWrapper<OperationAuditLog> wrapper = new LambdaQueryWrapper<OperationAuditLog>()
                .like(StringUtils.hasText(operator), OperationAuditLog::getOperator, operator)
                .eq(StringUtils.hasText(operationType), OperationAuditLog::getOperationType, operationType)
                .eq(StringUtils.hasText(resourceType), OperationAuditLog::getResourceType, resourceType)
                .eq(StringUtils.hasText(status), OperationAuditLog::getStatus, status)
                .orderByDesc(OperationAuditLog::getCreateTime);
        Page<OperationAuditLog> page = operationAuditLogService.page(
                new Page<>(query.getCurrent(), query.getSize()), wrapper);
        return Result.ok(PageUtil.convert(page));
    }
}
