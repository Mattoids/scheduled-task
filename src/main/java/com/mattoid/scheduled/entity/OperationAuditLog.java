package com.mattoid.scheduled.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("operation_audit_log")
public class OperationAuditLog extends BaseEntity {

    private String operator;

    private String operationType;

    private String resourceType;

    private Long resourceId;

    private String resourceName;

    private String requestUri;

    private String requestMethod;

    private String requestParams;

    private String oldValue;

    private String newValue;

    private String ipAddress;

    private String status;

    private String errorMessage;
}
