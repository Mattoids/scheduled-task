package com.mattoid.scheduled.task;

import com.mattoid.scheduled.entity.TaskConfig;

import java.util.Map;

/**
 * 任务处理器 SPI：按任务类型处理具体执行逻辑。
 */
public interface TaskHandler {

    /**
     * 是否支持该任务。
     */
    boolean supports(TaskConfig task);

    /**
     * 执行任务并返回结果。
     */
    TaskExecutionResult handle(TaskConfig task, Map<String, Object> params) throws Exception;
}
