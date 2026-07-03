package com.mattoid.scheduled.task;

import com.mattoid.scheduled.entity.TaskConfig;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Slf4j
@Service
public class TaskSchedulerService {

    private final Scheduler scheduler;

    public static final String JOB_GROUP = "REPORT_TASK";

    public TaskSchedulerService(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    public void scheduleTask(TaskConfig task) throws SchedulerException {
        removeTask(task.getId());

        if (!"ENABLE".equals(task.getStatus())) {
            return;
        }

        JobDetail jobDetail = JobBuilder.newJob(ReportTaskJob.class)
                .withIdentity(buildJobKey(task.getId()))
                .usingJobData(ReportTaskJob.TASK_ID_KEY, task.getId())
                .storeDurably()
                .build();

        Trigger trigger = buildTrigger(task);
        if (trigger == null) {
            log.warn("无法为任务 {} 构建触发器", task.getId());
            return;
        }

        scheduler.scheduleJob(jobDetail, trigger);
        log.info("Task {} scheduled with {}", task.getId(), task.getTriggerType());
    }

    public void removeTask(Long taskId) throws SchedulerException {
        JobKey jobKey = buildJobKey(taskId);
        if (scheduler.checkExists(jobKey)) {
            scheduler.deleteJob(jobKey);
            log.info("Task {} removed from scheduler", taskId);
        }
    }

    public void pauseTask(Long taskId) throws SchedulerException {
        JobKey jobKey = buildJobKey(taskId);
        if (scheduler.checkExists(jobKey)) {
            scheduler.pauseJob(jobKey);
        }
    }

    public void resumeTask(Long taskId) throws SchedulerException {
        JobKey jobKey = buildJobKey(taskId);
        if (scheduler.checkExists(jobKey)) {
            scheduler.resumeJob(jobKey);
        }
    }

    public void triggerOnce(Long taskId) throws SchedulerException {
        JobKey jobKey = buildJobKey(taskId);
        if (scheduler.checkExists(jobKey)) {
            scheduler.triggerJob(jobKey);
        } else {
            log.warn("任务 {} 未在调度器中注册，无法手动触发", taskId);
        }
    }

    private Trigger buildTrigger(TaskConfig task) {
        if ("CRON".equalsIgnoreCase(task.getTriggerType())) {
            return TriggerBuilder.newTrigger()
                    .withIdentity(buildTriggerKey(task.getId()))
                    .withSchedule(CronScheduleBuilder.cronSchedule(task.getTriggerConfig()))
                    .build();
        } else if ("ONCE".equalsIgnoreCase(task.getTriggerType())) {
            LocalDateTime executeTime = LocalDateTime.parse(task.getTriggerConfig());
            Date startTime = Date.from(executeTime.atZone(ZoneId.systemDefault()).toInstant());
            if (startTime.before(new Date())) {
                log.warn("一次性任务 {} 的执行时间 {} 已过期", task.getId(), executeTime);
                return null;
            }
            return TriggerBuilder.newTrigger()
                    .withIdentity(buildTriggerKey(task.getId()))
                    .startAt(startTime)
                    .build();
        }
        return null;
    }

    private JobKey buildJobKey(Long taskId) {
        return new JobKey("TASK_" + taskId, JOB_GROUP);
    }

    private TriggerKey buildTriggerKey(Long taskId) {
        return new TriggerKey("TRIGGER_" + taskId, JOB_GROUP);
    }
}
