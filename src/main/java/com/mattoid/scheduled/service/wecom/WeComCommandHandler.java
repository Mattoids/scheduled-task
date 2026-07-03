package com.mattoid.scheduled.service.wecom;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mattoid.scheduled.entity.TaskConfig;
import com.mattoid.scheduled.entity.TaskLog;
import com.mattoid.scheduled.mapper.TaskLogMapper;
import com.mattoid.scheduled.service.TaskConfigService;
import com.mattoid.scheduled.service.TaskExecutionService;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.cp.bean.message.WxCpXmlMessage;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class WeComCommandHandler {

    private static final int PAGE_SIZE = 10;

    private final TaskConfigService taskConfigService;
    private final TaskExecutionService taskExecutionService;
    private final TaskLogMapper taskLogMapper;

    public WeComCommandHandler(TaskConfigService taskConfigService,
                               TaskExecutionService taskExecutionService,
                               TaskLogMapper taskLogMapper) {
        this.taskConfigService = taskConfigService;
        this.taskExecutionService = taskExecutionService;
        this.taskLogMapper = taskLogMapper;
    }

    public String handle(WxCpXmlMessage message, Long configId) {
        String content = message.getContent();
        String eventKey = message.getEventKey();

        if (!StringUtils.hasText(content) && !StringUtils.hasText(eventKey)) {
            return "无法识别指令，请发送“帮助”查看可用指令。";
        }

        String command = StringUtils.hasText(eventKey) ? eventKey.trim() : content.trim();

        try {
            if ("QUERY_TASKS".equalsIgnoreCase(command) || command.startsWith("查询任务")) {
                return handleQueryTasks(command);
            }
            if (command.startsWith("运行 ") || command.startsWith("运行任务 ")) {
                return handleRunTask(command);
            }
            if (command.startsWith("创建任务 ")) {
                return handleCreateTask(command);
            }
            if ("RUN_TASK_PROMPT".equalsIgnoreCase(command)) {
                return "请回复：运行 {任务ID}\n例如：运行 1";
            }
            if ("HELP".equalsIgnoreCase(command) || "帮助".equals(command)) {
                return helpText();
            }
            return "未知指令：" + command + "\n请发送“帮助”查看可用指令。";
        } catch (Exception e) {
            log.error("企业微信指令处理失败: {}", command, e);
            return "指令处理失败: " + e.getMessage();
        }
    }

    private String handleQueryTasks(String command) {
        int page = 1;
        String[] parts = command.split("\\s+");
        if (parts.length > 1) {
            try {
                page = Integer.parseInt(parts[1]);
            } catch (NumberFormatException ignored) {
            }
        }
        Page<TaskConfig> result = taskConfigService.page(
                new Page<>(page, PAGE_SIZE),
                new LambdaQueryWrapper<TaskConfig>().orderByDesc(TaskConfig::getCreateTime)
        );
        List<TaskConfig> records = result.getRecords();
        if (records.isEmpty()) {
            return "暂无任务。";
        }
        StringBuilder sb = new StringBuilder("任务列表（第 " + page + " 页，共 " + result.getPages() + " 页）：\n");
        for (TaskConfig task : records) {
            sb.append(task.getId()).append(". ")
                    .append(task.getTaskName()).append(" [")
                    .append(task.getStatus()).append("]\n");
        }
        sb.append("\n回复“运行 {ID}”触发任务，回复“帮助”查看全部指令。");
        return sb.toString();
    }

    private String handleRunTask(String command) {
        String[] parts = command.split("\\s+");
        if (parts.length < 2) {
            return "请指定任务 ID，例如：运行 1";
        }
        Long taskId;
        try {
            taskId = Long.parseLong(parts[parts.length - 1]);
        } catch (NumberFormatException e) {
            return "任务 ID 必须是数字。";
        }
        TaskConfig task = taskConfigService.getById(taskId);
        if (task == null) {
            return "任务不存在: " + taskId;
        }
        taskExecutionService.executeTaskAsync(taskId, "MANUAL");
        return "已触发任务: " + task.getTaskName() + " (ID: " + taskId + ")，请稍后查看执行结果。";
    }

    private String handleCreateTask(String command) throws Exception {
        String payload = command.substring("创建任务 ".length()).trim();
        String[] parts = payload.split("\\|");
        if (parts.length < 4) {
            return "创建任务格式错误，正确格式：\n创建任务 任务名|任务编码|CRON表达式|sqlId1,sqlId2";
        }
        String taskName = parts[0].trim();
        String taskCode = parts[1].trim();
        String triggerConfig = parts[2].trim();
        List<Long> sqlIds = Arrays.stream(parts[3].split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(Long::parseLong)
                .collect(Collectors.toList());
        if (sqlIds.isEmpty()) {
            return "请至少指定一个 SQL ID。";
        }

        TaskConfig task = new TaskConfig();
        task.setTaskName(taskName);
        task.setTaskCode(taskCode);
        task.setTriggerType(triggerConfig.contains(" ") ? "CRON" : "ONCE");
        task.setTriggerConfig(triggerConfig);
        task.setStatus("ENABLE");
        taskConfigService.saveOrUpdateTask(task, sqlIds);
        return "任务创建成功: " + task.getTaskName() + " (ID: " + task.getId() + ")";
    }

    public String buildTaskResultSummary(Long taskId, TaskLog logEntity) {
        TaskConfig task = taskConfigService.getById(taskId);
        String taskName = task != null ? task.getTaskName() : String.valueOf(taskId);
        StringBuilder sb = new StringBuilder();
        sb.append("任务执行结果\n");
        sb.append("名称: ").append(taskName).append("\n");
        sb.append("状态: ").append(logEntity.getStatus()).append("\n");
        if (StringUtils.hasText(logEntity.getResultMessage())) {
            sb.append("结果: ").append(logEntity.getResultMessage()).append("\n");
        }
        if (StringUtils.hasText(logEntity.getErrorMessage())) {
            sb.append("错误: ").append(logEntity.getErrorMessage()).append("\n");
        }
        if (StringUtils.hasText(logEntity.getFilePath())) {
            sb.append("文件: ").append(logEntity.getFilePath());
        }
        return sb.toString();
    }

    private String helpText() {
        return "可用指令：\n" +
                "查询任务 [页码] - 查看任务列表\n" +
                "运行 {任务ID} - 手动运行任务\n" +
                "创建任务 任务名|任务编码|CRON表达式|sqlId1,sqlId2 - 创建任务\n" +
                "帮助 - 显示本帮助";
    }
}
