package com.mattoid.scheduled.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mattoid.scheduled.entity.OperationAuditLog;
import com.mattoid.scheduled.mapper.OperationAuditLogMapper;
import org.springframework.stereotype.Service;

@Service
public class OperationAuditLogService extends ServiceImpl<OperationAuditLogMapper, OperationAuditLog> {
}
