package com.mattoid.scheduled.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;

/**
 * 任务执行服务门面：负责提供同步/异步执行入口，具体执行逻辑由 TaskOrchestrator 编排。
 */
@Slf4j
@Service
public class TaskExecutionService {

    private final TaskOrchestrator taskOrchestrator;

    public TaskExecutionService(TaskOrchestrator taskOrchestrator) {
        this.taskOrchestrator = taskOrchestrator;
    }

    public void executeTask(Long taskId, String triggerMode) {
        executeTask(taskId, triggerMode, Collections.emptyMap());
    }

    public void executeTask(Long taskId, String triggerMode, Map<String, Object> params) {
        taskOrchestrator.executeTask(taskId, triggerMode, params);
    }

    @Async
    public void executeTaskAsync(Long taskId, String triggerMode) {
        executeTaskAsync(taskId, triggerMode, Collections.emptyMap());
    }

    @Async
    public void executeTaskAsync(Long taskId, String triggerMode, Map<String, Object> params) {
        executeTask(taskId, triggerMode, params);
    }

    @Async
    public void executeTaskAsyncWithDependencies(Long taskId, String triggerMode) {
        taskOrchestrator.executeTaskWithDependencies(taskId, triggerMode);
    }
}
