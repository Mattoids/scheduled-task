package com.mattoid.scheduled.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mattoid.scheduled.entity.TaskDependency;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TaskDependencyMapper extends BaseMapper<TaskDependency> {
}
