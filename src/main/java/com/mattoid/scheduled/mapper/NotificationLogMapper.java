package com.mattoid.scheduled.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mattoid.scheduled.entity.NotificationLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NotificationLogMapper extends BaseMapper<NotificationLog> {
}
