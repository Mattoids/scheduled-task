package com.mattoid.scheduled.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mattoid.scheduled.entity.TaskSqlConfig;
import com.mattoid.scheduled.entity.TaskSqlGroup;
import com.mattoid.scheduled.entity.TaskSqlRelation;
import com.mattoid.scheduled.mapper.TaskSqlConfigMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TaskSqlConfigService extends ServiceImpl<TaskSqlConfigMapper, TaskSqlConfig> {

    private final TaskSqlRelationService taskSqlRelationService;
    private final TaskSqlGroupService taskSqlGroupService;

    public TaskSqlConfigService(TaskSqlRelationService taskSqlRelationService,
                                TaskSqlGroupService taskSqlGroupService) {
        this.taskSqlRelationService = taskSqlRelationService;
        this.taskSqlGroupService = taskSqlGroupService;
    }

    @Override
    public boolean save(TaskSqlConfig config) {
        normalizeFileNamePattern(config);
        return super.save(config);
    }

    @Override
    public boolean updateById(TaskSqlConfig config) {
        normalizeFileNamePattern(config);
        lambdaUpdate()
                .set(TaskSqlConfig::getFileNamePattern, config.getFileNamePattern())
                .eq(TaskSqlConfig::getId, config.getId())
                .update();
        return super.updateById(config);
    }

    private void normalizeFileNamePattern(TaskSqlConfig config) {
        if (!StringUtils.hasText(config.getFileNamePattern())) {
            config.setFileNamePattern(null);
        }
        Long groupId = config.getGroupId();
        if (groupId != null) {
            TaskSqlGroup group = taskSqlGroupService.getById(groupId);
            if (group != null && StringUtils.hasText(group.getFileNamePattern())) {
                config.setFileNamePattern(null);
            }
        }
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
        populateGroups(sqlConfigs);
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

    public void populateGroups(List<TaskSqlConfig> configs) {
        if (configs == null || configs.isEmpty()) {
            return;
        }
        List<Long> groupIds = configs.stream()
                .map(TaskSqlConfig::getGroupId)
                .distinct()
                .filter(id -> id != null)
                .collect(Collectors.toList());
        if (groupIds.isEmpty()) {
            return;
        }
        Map<Long, TaskSqlGroup> groupMap = taskSqlGroupService.listByIds(groupIds).stream()
                .collect(Collectors.toMap(TaskSqlGroup::getId, g -> g));
        for (TaskSqlConfig config : configs) {
            TaskSqlGroup group = groupMap.get(config.getGroupId());
            if (group != null) {
                config.setTaskSqlGroup(group);
                config.setGroupName(group.getGroupName());
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean removeSqlConfig(Long sqlId) {
        taskSqlRelationService.lambdaUpdate()
                .eq(TaskSqlRelation::getSqlId, sqlId)
                .remove();
        return removeById(sqlId);
    }
}
