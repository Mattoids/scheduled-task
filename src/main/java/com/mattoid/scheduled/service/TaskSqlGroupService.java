package com.mattoid.scheduled.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mattoid.scheduled.entity.TaskSqlConfig;
import com.mattoid.scheduled.entity.TaskSqlGroup;
import com.mattoid.scheduled.mapper.TaskSqlConfigMapper;
import com.mattoid.scheduled.mapper.TaskSqlGroupMapper;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.List;

@Service
public class TaskSqlGroupService extends ServiceImpl<TaskSqlGroupMapper, TaskSqlGroup> {

    private final TaskSqlConfigMapper taskSqlConfigMapper;

    public TaskSqlGroupService(TaskSqlConfigMapper taskSqlConfigMapper) {
        this.taskSqlConfigMapper = taskSqlConfigMapper;
    }

    public List<TaskSqlGroup> listActive() {
        return lambdaQuery().eq(TaskSqlGroup::getStatus, 1).list();
    }

    @Override
    public boolean removeById(Serializable id) {
        taskSqlConfigMapper.update(null,
                new LambdaUpdateWrapper<TaskSqlConfig>()
                        .eq(TaskSqlConfig::getGroupId, id)
                        .set(TaskSqlConfig::getGroupId, null));
        return super.removeById(id);
    }
}
