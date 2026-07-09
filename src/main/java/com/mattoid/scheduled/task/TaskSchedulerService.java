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
            String cron = normalizeCronExpression(task.getTriggerConfig());
            if (cron == null) {
                log.warn("任务 {} 的 Cron 表达式无效: {}", task.getId(), task.getTriggerConfig());
                return null;
            }
            return TriggerBuilder.newTrigger()
                    .withIdentity(buildTriggerKey(task.getId()))
                    .withSchedule(CronScheduleBuilder.cronSchedule(cron))
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

    /**
     * 将常见 Cron 表达式规范化为 Quartz 可识别的格式。
     * Quartz 不允许同时指定 day-of-month 和 day-of-week，需将其中一个替换为 '?'。
     */
    static String normalizeCronExpression(String expression) {
        if (expression == null) {
            return null;
        }
        String trimmed = expression.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        String[] parts = trimmed.split("\\s+");
        if (parts.length == 5) {
            // Linux 风格 5 位：分 时 日 月 周 -> 补秒位
            String[] expanded = new String[6];
            expanded[0] = "0";
            System.arraycopy(parts, 0, expanded, 1, 5);
            parts = expanded;
        } else if (parts.length != 6 && parts.length != 7) {
            return null;
        }

        if (!"?".equals(parts[3]) && !"?".equals(parts[5])) {
            // 两者都被指定时，保留具体值的一方，另一方设为 '?'
            if ("*".equals(parts[5])) {
                parts[5] = "?";
            } else {
                parts[3] = "?";
            }
        }

        return String.join(" ", parts);
    }

    private JobKey buildJobKey(Long taskId) {
        return new JobKey("TASK_" + taskId, JOB_GROUP);
    }

    private TriggerKey buildTriggerKey(Long taskId) {
        return new TriggerKey("TRIGGER_" + taskId, JOB_GROUP);
    }
}
