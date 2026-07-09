package com.mattoid.scheduled.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mattoid.scheduled.entity.TaskConfig;
import com.mattoid.scheduled.entity.TaskDependency;
import com.mattoid.scheduled.entity.TaskLog;
import com.mattoid.scheduled.entity.TaskSqlConfig;
import com.mattoid.scheduled.entity.TaskSqlRelation;
import com.mattoid.scheduled.entity.TaskWebCrawlConfig;
import com.mattoid.scheduled.entity.TaskWebCrawlRelation;
import com.mattoid.scheduled.event.TaskExecutionEvent;
import com.mattoid.scheduled.mapper.TaskConfigMapper;
import com.mattoid.scheduled.task.TaskSchedulerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Slf4j
@Service
public class TaskConfigService extends ServiceImpl<TaskConfigMapper, TaskConfig> {

    private final TaskSchedulerService taskSchedulerService;
    private final TaskExecutionService taskExecutionService;
    private final TaskSqlConfigService taskSqlConfigService;
    private final TaskSqlRelationService taskSqlRelationService;
    private final TaskWebCrawlConfigService taskWebCrawlConfigService;
    private final TaskWebCrawlRelationService taskWebCrawlRelationService;
    private final TaskDependencyService taskDependencyService;
    private final TaskLogService taskLogService;

    public TaskConfigService(TaskSchedulerService taskSchedulerService,
                             TaskExecutionService taskExecutionService,
                             TaskSqlConfigService taskSqlConfigService,
                             TaskSqlRelationService taskSqlRelationService,
                             TaskWebCrawlConfigService taskWebCrawlConfigService,
                             TaskWebCrawlRelationService taskWebCrawlRelationService,
                             TaskDependencyService taskDependencyService,
                             TaskLogService taskLogService) {
        this.taskSchedulerService = taskSchedulerService;
        this.taskExecutionService = taskExecutionService;
        this.taskSqlConfigService = taskSqlConfigService;
        this.taskSqlRelationService = taskSqlRelationService;
        this.taskWebCrawlConfigService = taskWebCrawlConfigService;
        this.taskWebCrawlRelationService = taskWebCrawlRelationService;
        this.taskDependencyService = taskDependencyService;
        this.taskLogService = taskLogService;
    }

    @PostConstruct
    public void initScheduledTasks() {
        List<TaskConfig> tasks = lambdaQuery().eq(TaskConfig::getStatus, "ENABLE").list();
        Set<Long> tasksWithDependencies = taskDependencyService.list().stream()
                .map(TaskDependency::getTaskId)
                .collect(Collectors.toSet());
        for (TaskConfig task : tasks) {
            try {
                if (tasksWithDependencies.contains(task.getId())) {
                    log.info("任务 {} 存在上游依赖，由依赖任务完成后级联触发，不直接调度", task.getId());
                    continue;
                }
                taskSchedulerService.scheduleTask(task);
            } catch (Exception e) {
                log.error("初始化任务调度失败: {}", task.getId(), e);
            }
        }
        log.info("Initialized {} scheduled tasks", tasks.size());
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean saveOrUpdateTask(TaskConfig task, List<String> sqlCodes, List<String> crawlCodes) throws Exception {
        return saveOrUpdateTask(task, sqlCodes, crawlCodes, null);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean saveOrUpdateTask(TaskConfig task, List<String> sqlCodes,
                                    List<String> crawlCodes, List<Long> dependencyIds) throws Exception {
        validateInWecomMenuLimit(task);
        boolean result = saveOrUpdate(task);
        Long taskId = task.getId();
        String taskCode = task.getTaskCode();
        if (StringUtils.hasText(taskCode)) {
            if ("CRAWL".equalsIgnoreCase(task.getTaskType())) {
                saveTaskSqlRelations(taskCode, Collections.emptyList());
                saveTaskCrawlRelations(taskCode, crawlCodes);
            } else {
                saveTaskCrawlRelations(taskCode, Collections.emptyList());
                saveTaskSqlRelations(taskCode, sqlCodes);
            }
            if (dependencyIds != null) {
                taskDependencyService.saveDependencies(taskId, dependencyIds);
            }
        }
        if ("ENABLE".equals(task.getStatus()) && taskDependencyService.getDependencyIds(taskId).isEmpty()) {
            taskSchedulerService.scheduleTask(task);
        } else {
            taskSchedulerService.removeTask(taskId);
        }
        return result;
    }

    private void validateInWecomMenuLimit(TaskConfig task) {
        if (task == null || !Integer.valueOf(1).equals(task.getInWecomMenu())) {
            return;
        }
        LambdaQueryWrapper<TaskConfig> wrapper = new LambdaQueryWrapper<TaskConfig>()
                .eq(TaskConfig::getInWecomMenu, 1);
        if (task.getId() != null) {
            wrapper.ne(TaskConfig::getId, task.getId());
        }
        long count = count(wrapper);
        if (count >= 5) {
            throw new IllegalArgumentException("最多只能开启 5 个任务加入企业微信应用菜单");
        }
    }

    private void saveTaskSqlRelations(String taskCode, List<String> sqlCodes) {
        // 先删除旧关系
        taskSqlRelationService.lambdaUpdate()
                .eq(TaskSqlRelation::getTaskCode, taskCode)
                .remove();
        if (CollectionUtils.isEmpty(sqlCodes)) {
            return;
        }
        // 去重并保留顺序
        List<String> distinctSqlCodes = sqlCodes.stream()
                .distinct()
                .collect(Collectors.toList());
        List<TaskSqlRelation> relations = IntStream.range(0, distinctSqlCodes.size())
                .mapToObj(i -> {
                    TaskSqlRelation relation = new TaskSqlRelation();
                    relation.setTaskCode(taskCode);
                    relation.setSqlCode(distinctSqlCodes.get(i));
                    relation.setSortOrder(i);
                    return relation;
                })
                .collect(Collectors.toList());
        taskSqlRelationService.saveBatch(relations);
    }

    public List<String> getTaskSqlCodes(String taskCode) {
        if (!StringUtils.hasText(taskCode)) {
            return Collections.emptyList();
        }
        return taskSqlRelationService.lambdaQuery()
                .eq(TaskSqlRelation::getTaskCode, taskCode)
                .orderByAsc(TaskSqlRelation::getSortOrder)
                .list()
                .stream()
                .map(TaskSqlRelation::getSqlCode)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
    }

    public List<TaskSqlConfig> getTaskSqlConfigs(Long taskId) {
        TaskConfig task = getById(taskId);
        return task != null ? taskSqlConfigService.listByTaskCode(task.getTaskCode()) : Collections.emptyList();
    }

    private void saveTaskCrawlRelations(String taskCode, List<String> crawlCodes) {
        taskWebCrawlRelationService.lambdaUpdate()
                .eq(TaskWebCrawlRelation::getTaskCode, taskCode)
                .remove();
        if (CollectionUtils.isEmpty(crawlCodes)) {
            return;
        }
        List<String> distinctCrawlCodes = crawlCodes.stream()
                .distinct()
                .collect(Collectors.toList());
        List<TaskWebCrawlRelation> relations = IntStream.range(0, distinctCrawlCodes.size())
                .mapToObj(i -> {
                    TaskWebCrawlRelation relation = new TaskWebCrawlRelation();
                    relation.setTaskCode(taskCode);
                    relation.setCrawlCode(distinctCrawlCodes.get(i));
                    relation.setSortOrder(i);
                    return relation;
                })
                .collect(Collectors.toList());
        taskWebCrawlRelationService.saveBatch(relations);
    }

    public List<String> getTaskCrawlCodes(String taskCode) {
        if (!StringUtils.hasText(taskCode)) {
            return Collections.emptyList();
        }
        return taskWebCrawlRelationService.lambdaQuery()
                .eq(TaskWebCrawlRelation::getTaskCode, taskCode)
                .orderByAsc(TaskWebCrawlRelation::getSortOrder)
                .list()
                .stream()
                .map(TaskWebCrawlRelation::getCrawlCode)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
    }

    public List<TaskWebCrawlConfig> getTaskWebCrawlConfigs(Long taskId) {
        TaskConfig task = getById(taskId);
        return task != null ? taskWebCrawlConfigService.listByTaskCode(task.getTaskCode()) : Collections.emptyList();
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
        TaskConfig task = getById(taskId);
        if (task == null) {
            return false;
        }
        taskSchedulerService.removeTask(taskId);
        taskSqlRelationService.lambdaUpdate()
                .eq(TaskSqlRelation::getTaskCode, task.getTaskCode())
                .remove();
        taskWebCrawlRelationService.lambdaUpdate()
                .eq(TaskWebCrawlRelation::getTaskCode, task.getTaskCode())
                .remove();
        taskDependencyService.removeByTaskId(taskId);
        return removeById(taskId);
    }

    public void triggerTask(Long taskId) {
        TaskConfig task = getById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在");
        }
        taskExecutionService.executeTaskAsyncWithDependencies(taskId, "MANUAL");
    }

    @Async
    @EventListener
    public void onTaskSuccess(TaskExecutionEvent event) {
        if (event.getEventType() != TaskExecutionEvent.EventType.TASK_SUCCESS) {
            return;
        }
        Long taskId = event.getTask().getId();
        List<Long> dependents = taskDependencyService.getDependentIds(taskId);
        for (Long dependentId : dependents) {
            if (areDependenciesMet(dependentId)) {
                log.info("任务 {} 的依赖已满足，触发下游任务 {}", taskId, dependentId);
                taskExecutionService.executeTaskAsync(dependentId, "AUTO");
            }
        }
    }

    private boolean areDependenciesMet(Long taskId) {
        List<Long> deps = taskDependencyService.getDependencyIds(taskId);
        for (Long depId : deps) {
            TaskLog latest = taskLogService.lambdaQuery()
                    .eq(TaskLog::getTaskId, depId)
                    .orderByDesc(TaskLog::getId)
                    .last("LIMIT 1")
                    .one();
            if (latest == null || !"SUCCESS".equals(latest.getStatus())) {
                return false;
            }
        }
        return true;
    }
}
