package com.mattoid.scheduled.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mattoid.scheduled.entity.TaskSqlConfig;
import com.mattoid.scheduled.entity.TaskSqlRelation;
import com.mattoid.scheduled.mapper.TaskSqlConfigMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TaskSqlConfigService extends ServiceImpl<TaskSqlConfigMapper, TaskSqlConfig> {

    private final TaskSqlRelationService taskSqlRelationService;

    public TaskSqlConfigService(TaskSqlRelationService taskSqlRelationService) {
        this.taskSqlRelationService = taskSqlRelationService;
    }

    public List<TaskSqlConfig> listByTaskId(Long taskId) {
        if (taskId == null) {
            return Collections.emptyList();
        }
        List<TaskSqlRelation> relations = taskSqlRelationService.lambdaQuery()
                .eq(TaskSqlRelation::getTaskId, taskId)
                .orderByAsc(TaskSqlRelation::getSortOrder)
                .list();
        if (relations.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> sqlIds = relations.stream()
                .map(TaskSqlRelation::getSqlId)
                .collect(Collectors.toList());
        List<TaskSqlConfig> sqlConfigs = listByIds(sqlIds);
        // 按 relation 的 sort_order 排序并回填
        return relations.stream()
                .map(relation -> {
                    TaskSqlConfig config = sqlConfigs.stream()
                            .filter(sql -> sql.getId().equals(relation.getSqlId()))
                            .findFirst()
                            .orElse(null);
                    if (config != null) {
                        config.setSortOrder(relation.getSortOrder());
                    }
                    return config;
                })
                .filter(config -> config != null)
                .collect(Collectors.toList());
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean removeSqlConfig(Long sqlId) {
        // 删除关联关系
        taskSqlRelationService.lambdaUpdate()
                .eq(TaskSqlRelation::getSqlId, sqlId)
                .remove();
        return removeById(sqlId);
    }
}
