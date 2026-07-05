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

import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class WeComCommandHandler {

    private static final int PAGE_SIZE = 10;
    private static final int TOP_TASKS_COUNT = 3;

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
            return "无法识别指令，请发送\"帮助\"查看可用指令。";
        }

        String command = StringUtils.hasText(eventKey) ? eventKey.trim() : content.trim();

        try {
            if ("QUERY_TASKS".equalsIgnoreCase(command) || command.startsWith("查询任务") || "任务列表".equals(command)) {
                return handleQueryTasks(command);
            }
            if (command.startsWith("查看任务")) {
                return handleViewTask(command);
            }
            if (command.startsWith("任务日志")) {
                return handleTaskLogs(command);
            }
            if ("最近任务".equals(command) || "RECENT_TASKS".equalsIgnoreCase(command)) {
                return handleRecentTasks();
            }
            if (command.startsWith("RUN_TASK_")) {
                return handleQuickRun(command);
            }
            if (command.startsWith("运行 ") || command.startsWith("运行任务 ") || command.startsWith("运行")) {
                return handleRunTask(command);
            }
            if (command.startsWith("创建任务 ")) {
                return handleCreateTask(command);
            }
            if ("RUN_TASK_PROMPT".equalsIgnoreCase(command)) {
                return "请回复：运行 {任务ID 或 任务名称}\n例如：运行 1\n例如：运行销售日报任务";
            }
            if ("HELP".equalsIgnoreCase(command) || "帮助".equals(command)) {
                return helpText();
            }
            return "未知指令：" + command + "\n请发送\"帮助\"查看可用指令。";
        } catch (Exception e) {
            log.error("企业微信指令处理失败: {}", command, e);
            return "指令处理失败: " + e.getMessage();
        }
    }

    private String handleViewTask(String command) {
        String arg = command.substring("查看任务".length()).trim();
        if (!StringUtils.hasText(arg)) {
            return "请指定任务 ID，例如：查看任务 1";
        }
        if (!arg.matches("\\d+")) {
            return "请使用任务 ID 查看，例如：查看任务 1";
        }
        TaskConfig task = taskConfigService.getById(Long.parseLong(arg));
        if (task == null) {
            return "任务不存在: " + arg;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("任务详情：\n");
        sb.append("ID: ").append(task.getId()).append("\n");
        sb.append("名称: ").append(task.getTaskName()).append("\n");
        sb.append("编码: ").append(task.getTaskCode() != null ? task.getTaskCode() : "无").append("\n");
        sb.append("状态: ").append(task.getStatus()).append("\n");
        sb.append("触发类型: ").append(task.getTriggerType() != null ? task.getTriggerType() : "无").append("\n");
        if (StringUtils.hasText(task.getTriggerConfig())) {
            sb.append("触发配置: ").append(task.getTriggerConfig()).append("\n");
        }
        // Get last execution info
        TaskLog lastLog = taskLogMapper.selectOne(
                new LambdaQueryWrapper<TaskLog>()
                        .eq(TaskLog::getTaskId, task.getId())
                        .orderByDesc(TaskLog::getCreateTime)
                        .last("LIMIT 1")
        );
        if (lastLog != null) {
            sb.append("最近执行: ").append(lastLog.getStartTime() != null ? lastLog.getStartTime().format(DateTimeFormatter.ofPattern("MM-dd HH:mm")) : "未知");
            if ("SUCCESS".equals(lastLog.getStatus())) {
                sb.append(" 成功");
            } else if ("FAILED".equals(lastLog.getStatus())) {
                sb.append(" 失败");
            } else {
                sb.append(" ").append(lastLog.getStatus());
            }
            sb.append("\n");
        }
        sb.append("\n回复\"运行 ").append(task.getId()).append("\" 立即运行本任务。");
        return sb.toString();
    }

    private String handleTaskLogs(String command) {
        String arg = command.substring("任务日志".length()).trim();
        if (!StringUtils.hasText(arg)) {
            return "请指定任务 ID，例如：任务日志 1";
        }
        String[] parts = arg.split("\\s+");
        if (!parts[0].matches("\\d+")) {
            return "请使用任务 ID 查看日志，例如：任务日志 1";
        }
        Long taskId = Long.parseLong(parts[0]);
        TaskConfig task = taskConfigService.getById(taskId);
        if (task == null) {
            return "任务不存在: " + parts[0];
        }
        int page = 1;
        if (parts.length > 1) {
            try {
                page = Integer.parseInt(parts[1]);
            } catch (NumberFormatException ignored) {
            }
        }
        Page<TaskLog> result = taskLogMapper.selectPage(
                new Page<>(page, PAGE_SIZE),
                new LambdaQueryWrapper<TaskLog>()
                        .eq(TaskLog::getTaskId, taskId)
                        .orderByDesc(TaskLog::getCreateTime)
        );
        List<TaskLog> records = result.getRecords();
        if (records.isEmpty()) {
            return "任务 \"" + task.getTaskName() + "\" 暂无执行记录。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("任务 \"" + task.getTaskName() + "\" 执行记录，");
        sb.append("第 ").append(page).append("/").append(result.getPages()).append(" 页：\n");
        for (TaskLog log : records) {
            String timeStr = log.getStartTime() != null
                    ? log.getStartTime().format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
                    : "未知";
            sb.append("[").append(log.getStatus()).append("] ").append(timeStr);
            if (StringUtils.hasText(log.getResultMessage())) {
                sb.append("\n  ").append(log.getResultMessage());
            }
            if (StringUtils.hasText(log.getErrorMessage())) {
                sb.append("\n  错误: ").append(log.getErrorMessage());
            }
            sb.append("\n");
        }
        sb.append("回复\"任务日志 ").append(taskId).append(" {页码}\" 查看其他页。");
        return sb.toString();
    }

    private String handleRecentTasks() {
        // Get top 3 most recently executed tasks
        List<TaskLog> recentLogs = taskLogMapper.selectList(
                new LambdaQueryWrapper<TaskLog>()
                        .ne(TaskLog::getStatus, "RUNNING")
                        .orderByDesc(TaskLog::getCreateTime)
                        .last("LIMIT " + TOP_TASKS_COUNT)
        );
        if (recentLogs.isEmpty()) {
            return "暂无最近执行的任务。请发送\"任务列表\"查看所有任务。";
        }

        StringBuilder sb = new StringBuilder("最近执行的任务（点击快捷按钮或回复序号运行）：\n");
        for (int i = 0; i < recentLogs.size(); i++) {
            TaskLog log = recentLogs.get(i);
            TaskConfig task = taskConfigService.getById(log.getTaskId());
            if (task == null) continue;
            String timeStr = log.getStartTime() != null
                    ? log.getStartTime().format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
                    : "未知";
            String statusLabel = "SUCCESS".equals(log.getStatus()) ? "成功" : "FAILED".equals(log.getStatus()) ? "失败" : log.getStatus();
            sb.append(i + 1).append(". ").append(task.getTaskName())
                    .append(" [").append(statusLabel).append("] ").append(timeStr).append("\n");
        }
        sb.append("\n回复序号（如\"1\"）即可运行对应任务，或回复\"任务列表\"查看全部。");
        return sb.toString();
    }

    private String handleQuickRun(String command) {
        // Command format: RUN_TASK_{taskId}
        String taskIdStr = command.substring("RUN_TASK_".length()).trim();
        if (!taskIdStr.matches("\\d+")) {
            return "快捷运行参数错误，请重新点击菜单或发送\"运行 {任务ID}\"。";
        }
        Long taskId = Long.parseLong(taskIdStr);
        TaskConfig task = taskConfigService.getById(taskId);
        if (task == null) {
            return "任务不存在: " + taskId;
        }
        taskExecutionService.executeTaskAsync(taskId, "MANUAL");
        return "已触发任务: " + task.getTaskName() + " (ID: " + taskId + ")，请稍后查看执行结果。";
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
            return "当前系统中暂无任务。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("当前系统共有 ").append(result.getTotal()).append(" 个任务，");
        sb.append("第 ").append(page).append("/").append(result.getPages()).append(" 页：\n");
        for (TaskConfig task : records) {
            sb.append(task.getId()).append(". ")
                    .append(task.getTaskName()).append(" [")
                    .append(task.getStatus()).append("]\n");
        }
        sb.append("\n回复\"运行 {ID 或 任务名称}\"触发任务，回复\"帮助\"查看全部指令。");
        return sb.toString();
    }

    private String handleRunTask(String command) {
        String arg = command.substring("运行".length()).trim();
        if (arg.startsWith("任务")) {
            arg = arg.substring("任务".length()).trim();
        }
        if (!StringUtils.hasText(arg)) {
            return "请指定任务 ID 或任务名称，例如：运行 1\n例如：运行销售日报任务";
        }

        TaskConfig task;
        if (arg.matches("\\d+")) {
            Long taskId = Long.parseLong(arg);
            task = taskConfigService.getById(taskId);
            if (task == null) {
                return "任务不存在: " + taskId;
            }
        } else {
            String name = arg;
            if (name.endsWith("任务")) {
                name = name.substring(0, name.length() - "任务".length());
            }
            List<TaskConfig> tasks = taskConfigService.lambdaQuery()
                    .like(TaskConfig::getTaskName, name)
                    .list();
            if (tasks.isEmpty()) {
                return "未找到匹配的任务: " + name;
            }
            if (tasks.size() > 1) {
                StringBuilder sb = new StringBuilder("找到多个匹配任务，请使用任务 ID 运行：\n");
                for (TaskConfig t : tasks) {
                    sb.append(t.getId()).append(". ").append(t.getTaskName()).append("\n");
                }
                return sb.toString();
            }
            task = tasks.get(0);
        }

        taskExecutionService.executeTaskAsync(task.getId(), "MANUAL");
        return "已触发任务: " + task.getTaskName() + " (ID: " + task.getId() + ")，请稍后查看执行结果。";
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
                "帮助 - 显示本帮助\n" +
                "任务列表 [页码] - 查看任务列表及总数\n" +
                "查询任务 [页码] - 同\"任务列表\"\n" +
                "查看任务 {ID} - 查看任务详情及最近执行结果\n" +
                "任务日志 {ID} [页码] - 查看任务执行日志\n" +
                "最近任务 - 查看最近执行的3个任务及快捷运行\n" +
                "运行 {任务ID} - 按 ID 手动运行任务\n" +
                "运行{任务名称}任务 - 按名称手动运行任务\n" +
                "创建任务 任务名|任务编码|CRON表达式|sqlId1,sqlId2 - 创建任务";
    }
}