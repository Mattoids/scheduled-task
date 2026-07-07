package com.mattoid.scheduled.task;

import com.mattoid.scheduled.service.TaskExecutionService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@DisallowConcurrentExecution
public class ReportTaskJob implements Job {

    public static final String TASK_ID_KEY = "taskId";

    private final TaskExecutionService taskExecutionService;

    public ReportTaskJob(TaskExecutionService taskExecutionService) {
        this.taskExecutionService = taskExecutionService;
    }

    @Override
    public void execute(JobExecutionContext context) {
        Long taskId = context.getMergedJobDataMap().getLong(TASK_ID_KEY);
        log.info("Quartz job triggered for task {}", taskId);
        taskExecutionService.executeTask(taskId, "AUTO");
    }
}
