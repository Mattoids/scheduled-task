package com.mattoid.scheduled.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mattoid.scheduled.entity.TaskConfig;
import com.mattoid.scheduled.entity.TaskSqlConfig;
import com.mattoid.scheduled.entity.TaskSqlGroup;
import com.mattoid.scheduled.entity.TaskSqlRelation;
import com.mattoid.scheduled.mapper.TaskConfigMapper;
import com.mattoid.scheduled.mapper.TaskSqlConfigMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class TaskSqlConfigService extends ServiceImpl<TaskSqlConfigMapper, TaskSqlConfig> {

    private final TaskSqlRelationService taskSqlRelationService;
    private final TaskSqlGroupService taskSqlGroupService;
    private final TaskConfigMapper taskConfigMapper;

    public TaskSqlConfigService(TaskSqlRelationService taskSqlRelationService,
                                TaskSqlGroupService taskSqlGroupService,
                                TaskConfigMapper taskConfigMapper) {
        this.taskSqlRelationService = taskSqlRelationService;
        this.taskSqlGroupService = taskSqlGroupService;
        this.taskConfigMapper = taskConfigMapper;
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
        String groupCode = config.getGroupCode();
        if (StringUtils.hasText(groupCode)) {
            TaskSqlGroup group = taskSqlGroupService.lambdaQuery()
                    .eq(TaskSqlGroup::getGroupCode, groupCode)
                    .one();
            if (group != null && StringUtils.hasText(group.getFileNamePattern())) {
                config.setFileNamePattern(null);
            }
        }
    }

    public List<TaskSqlConfig> listByTaskId(Long taskId) {
        if (taskId == null) {
            return Collections.emptyList();
        }
        TaskConfig task = taskConfigMapper.selectById(taskId);
        return task != null ? listByTaskCode(task.getTaskCode()) : Collections.emptyList();
    }

    public List<TaskSqlConfig> listByTaskCode(String taskCode) {
        if (!StringUtils.hasText(taskCode)) {
            return Collections.emptyList();
        }
        List<String> sqlCodes = taskSqlRelationService.lambdaQuery()
                .eq(TaskSqlRelation::getTaskCode, taskCode)
                .orderByAsc(TaskSqlRelation::getSortOrder)
                .list()
                .stream()
                .map(TaskSqlRelation::getSqlCode)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
        return listBySqlCodes(sqlCodes);
    }

    private List<TaskSqlConfig> listBySqlCodes(List<String> sqlCodes) {
        if (sqlCodes.isEmpty()) {
            return Collections.emptyList();
        }
        List<TaskSqlConfig> sqlConfigs = lambdaQuery()
                .in(TaskSqlConfig::getSqlCode, sqlCodes)
                .list();
        populateGroups(sqlConfigs);
        // 保持与 relation 一致的顺序
        Map<String, TaskSqlConfig> configMap = sqlConfigs.stream()
                .collect(Collectors.toMap(TaskSqlConfig::getSqlCode, c -> c, (a, b) -> a));
        return sqlCodes.stream()
                .map(configMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public void populateGroups(List<TaskSqlConfig> configs) {
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
                .list()
                .stream()
                .collect(Collectors.toMap(TaskSqlGroup::getGroupCode, g -> g));
        for (TaskSqlConfig config : configs) {
            TaskSqlGroup group = groupMap.get(config.getGroupCode());
            if (group != null) {
                config.setTaskSqlGroup(group);
                config.setGroupName(group.getGroupName());
            }
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean removeSqlConfig(Long sqlId) {
        TaskSqlConfig config = getById(sqlId);
        if (config != null && StringUtils.hasText(config.getSqlCode())) {
            taskSqlRelationService.lambdaUpdate()
                    .eq(TaskSqlRelation::getSqlCode, config.getSqlCode())
                    .remove();
        }
        return removeById(sqlId);
    }
}
