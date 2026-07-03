package com.mattoid.scheduled.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mattoid.scheduled.entity.TaskConfig;
import com.mattoid.scheduled.mapper.TaskConfigMapper;
import com.mattoid.scheduled.task.TaskSchedulerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.util.List;

@Slf4j
@Service
public class TaskConfigService extends ServiceImpl<TaskConfigMapper, TaskConfig> {

    private final TaskSchedulerService taskSchedulerService;
    private final TaskExecutionService taskExecutionService;

    public TaskConfigService(TaskSchedulerService taskSchedulerService,
                             TaskExecutionService taskExecutionService) {
        this.taskSchedulerService = taskSchedulerService;
        this.taskExecutionService = taskExecutionService;
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
    public boolean saveOrUpdateTask(TaskConfig task) throws Exception {
        boolean result = saveOrUpdate(task);
        taskSchedulerService.scheduleTask(task);
        return result;
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
