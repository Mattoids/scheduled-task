package com.mattoid.scheduled.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mattoid.scheduled.entity.TaskConfig;
import com.mattoid.scheduled.entity.TaskLog;
import com.mattoid.scheduled.mapper.TaskConfigMapper;
import com.mattoid.scheduled.mapper.TaskLogMapper;
import com.mattoid.scheduled.task.TaskExecutionResult;
import com.mattoid.scheduled.task.TaskHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 任务编排器：负责任务加载、并发控制、处理器路由、日志更新与事件分发。
 */
@Slf4j
@Component
public class TaskOrchestrator {

    private final TaskConfigMapper taskConfigMapper;
    private final TaskLogMapper taskLogMapper;
    private final List<TaskHandler> handlers;
    private final ReportAssembler reportAssembler;
    private final NotificationDispatcher notificationDispatcher;
    private final TaskDependencyService taskDependencyService;

    public TaskOrchestrator(TaskConfigMapper taskConfigMapper,
                            TaskLogMapper taskLogMapper,
                            List<TaskHandler> handlers,
                            ReportAssembler reportAssembler,
                            NotificationDispatcher notificationDispatcher,
                            TaskDependencyService taskDependencyService) {
        this.taskConfigMapper = taskConfigMapper;
        this.taskLogMapper = taskLogMapper;
        this.handlers = handlers;
        this.reportAssembler = reportAssembler;
        this.notificationDispatcher = notificationDispatcher;
        this.taskDependencyService = taskDependencyService;
    }

    public TaskLog executeTask(Long taskId, String triggerMode) {
        return executeTask(taskId, triggerMode, Collections.emptyMap());
    }

    public TaskLog executeTask(Long taskId, String triggerMode, Map<String, Object> params) {
        TaskConfig task = taskConfigMapper.selectById(taskId);
        if (task == null) {
            log.error("任务不存在: {}", taskId);
            return null;
        }

        TaskLog logEntity = createRunningLog(taskId, triggerMode);
        if (isAlreadyRunning(taskId, logEntity.getId())) {
            log.warn("任务正在执行中，跳过本次触发: taskId={}", taskId);
            logEntity.setStatus("SKIPPED");
            logEntity.setResultMessage("任务正在执行中，已跳过本次触发");
            logEntity.setEndTime(LocalDateTime.now());
            taskLogMapper.updateById(logEntity);
            return logEntity;
        }

        TaskExecutionResult result = new TaskExecutionResult();
        try {
            TaskHandler handler = selectHandler(task);
            result = handler.handle(task, params);
            logEntity.setStatus("SUCCESS");
            logEntity.setResultMessage(reportAssembler.assembleResultMessage(result));
            logEntity.setFilePath(reportAssembler.assembleFilePath(result));
        } catch (Exception e) {
            log.error("任务执行失败: {}", taskId, e);
            logEntity.setStatus("FAILED");
            logEntity.setErrorMessage(e.getMessage());
        } finally {
            logEntity.setEndTime(LocalDateTime.now());
            taskLogMapper.updateById(logEntity);
            notificationDispatcher.dispatch(task, logEntity, result);
        }
        return logEntity;
    }

    public void executeTaskWithDependencies(Long taskId, String triggerMode) {
        List<Long> sorted = taskDependencyService.topologicalSort(taskId);
        for (Long id : sorted) {
            TaskLog latestLog = executeTask(id, triggerMode);
            if (latestLog == null || !"SUCCESS".equals(latestLog.getStatus())) {
                log.warn("依赖任务 {} 未成功执行，中止后续任务", id);
                break;
            }
        }
    }

    private TaskLog createRunningLog(Long taskId, String triggerMode) {
        TaskLog logEntity = new TaskLog();
        logEntity.setTaskId(taskId);
        logEntity.setTriggerMode(triggerMode);
        logEntity.setStartTime(LocalDateTime.now());
        logEntity.setStatus("RUNNING");
        taskLogMapper.insert(logEntity);
        return logEntity;
    }

    private boolean isAlreadyRunning(Long taskId, Long currentLogId) {
        Long runningCount = taskLogMapper.selectCount(
                new LambdaQueryWrapper<TaskLog>()
                        .eq(TaskLog::getTaskId, taskId)
                        .eq(TaskLog::getStatus, "RUNNING")
                        .lt(TaskLog::getId, currentLogId)
                        .ge(TaskLog::getStartTime, LocalDateTime.now().minusHours(1))
        );
        return runningCount != null && runningCount > 0;
    }

    private TaskHandler selectHandler(TaskConfig task) {
        for (TaskHandler handler : handlers) {
            if (handler.supports(task)) {
                return handler;
            }
        }
        throw new IllegalStateException("未找到支持任务类型的处理器: " + task.getTaskType());
    }
}
