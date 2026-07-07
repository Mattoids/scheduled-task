package com.mattoid.scheduled.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mattoid.scheduled.entity.OperationAuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OperationAuditLogMapper extends BaseMapper<OperationAuditLog> {
}
