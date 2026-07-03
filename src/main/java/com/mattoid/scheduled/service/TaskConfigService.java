package com.mattoid.scheduled.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mattoid.scheduled.entity.TaskConfig;
import com.mattoid.scheduled.entity.TaskSqlConfig;
import com.mattoid.scheduled.entity.TaskSqlRelation;
import com.mattoid.scheduled.mapper.TaskConfigMapper;
import com.mattoid.scheduled.task.TaskSchedulerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import jakarta.annotation.PostConstruct;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Service
public class TaskConfigService extends ServiceImpl<TaskConfigMapper, TaskConfig> {

    private final TaskSchedulerService taskSchedulerService;
    private final TaskExecutionService taskExecutionService;
    private final TaskSqlConfigService taskSqlConfigService;
    private final TaskSqlRelationService taskSqlRelationService;

    public TaskConfigService(TaskSchedulerService taskSchedulerService,
                             TaskExecutionService taskExecutionService,
                             TaskSqlConfigService taskSqlConfigService,
                             TaskSqlRelationService taskSqlRelationService) {
        this.taskSchedulerService = taskSchedulerService;
        this.taskExecutionService = taskExecutionService;
        this.taskSqlConfigService = taskSqlConfigService;
        this.taskSqlRelationService = taskSqlRelationService;
    }

    @PostConstruct
    public void initScheduledTasks() {
        List<TaskConfig> tasks = lambdaQuery().eq(TaskConfig::getStatus, "ENABLE").list();
        for (TaskConfig task : tasks) {
            try {
                taskSchedulerService.scheduleTask(task);
            } catch (Exception e) {
                log.error("初始化任务调度失败: {}", task.getId(), e);
            }
        }
        log.info("Initialized {} scheduled tasks", tasks.size());
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean saveOrUpdateTask(TaskConfig task, List<Long> sqlIds) throws Exception {
        boolean result = saveOrUpdate(task);
        Long taskId = task.getId();
        if (taskId != null) {
            saveTaskSqlRelations(taskId, sqlIds);
        }
        taskSchedulerService.scheduleTask(task);
        return result;
    }

    private void saveTaskSqlRelations(Long taskId, List<Long> sqlIds) {
        // 先删除旧关系
        taskSqlRelationService.lambdaUpdate()
                .eq(TaskSqlRelation::getTaskId, taskId)
                .remove();
        if (CollectionUtils.isEmpty(sqlIds)) {
            return;
        }
        List<TaskSqlRelation> relations = IntStream.range(0, sqlIds.size())
                .mapToObj(i -> {
                    TaskSqlRelation relation = new TaskSqlRelation();
                    relation.setTaskId(taskId);
                    relation.setSqlId(sqlIds.get(i));
                    relation.setSortOrder(i);
                    return relation;
                })
                .collect(Collectors.toList());
        taskSqlRelationService.saveBatch(relations);
    }

    public List<Long> getTaskSqlIds(Long taskId) {
        if (taskId == null) {
            return Collections.emptyList();
        }
        return taskSqlRelationService.lambdaQuery()
                .eq(TaskSqlRelation::getTaskId, taskId)
                .orderByAsc(TaskSqlRelation::getSortOrder)
                .list()
                .stream()
                .map(TaskSqlRelation::getSqlId)
                .collect(Collectors.toList());
    }

    public List<TaskSqlConfig> getTaskSqlConfigs(Long taskId) {
        return taskSqlConfigService.listByTaskId(taskId);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean updateStatus(Long taskId, String status) throws Exception {
        TaskConfig task = getById(taskId);
        if (task == null) {
            return false;
        }
        task.setStatus(status);
        boolean result = updateById(task);
        if ("ENABLE".equals(status)) {
            taskSchedulerService.scheduleTask(task);
        } else {
            taskSchedulerService.removeTask(taskId);
        }
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean removeTask(Long taskId) throws Exception {
        taskSchedulerService.removeTask(taskId);
        taskSqlRelationService.lambdaUpdate()
                .eq(TaskSqlRelation::getTaskId, taskId)
                .remove();
        return removeById(taskId);
    }

    public void triggerTask(Long taskId) {
        TaskConfig task = getById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在");
        }
        taskExecutionService.executeTask(taskId, "MANUAL");
    }
}
