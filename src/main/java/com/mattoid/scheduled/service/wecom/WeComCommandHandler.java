package com.mattoid.scheduled.service.wecom;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mattoid.scheduled.dto.CommandResult;
import com.mattoid.scheduled.dto.IntentResult;
import com.mattoid.scheduled.entity.DatasourceConfig;
import com.mattoid.scheduled.entity.TaskConfig;
import com.mattoid.scheduled.entity.TaskLog;
import com.mattoid.scheduled.entity.TaskSqlConfig;
import com.mattoid.scheduled.mapper.TaskLogMapper;
import com.mattoid.scheduled.service.AiAssistantService;
import com.mattoid.scheduled.service.AiConversationService;
import com.mattoid.scheduled.service.AiKnowledgeDocService;
import com.mattoid.scheduled.service.ChartGenerationService;
import com.mattoid.scheduled.service.DatasourceConfigService;
import com.mattoid.scheduled.service.TaskConfigService;
import com.mattoid.scheduled.service.TaskExecutionService;
import com.mattoid.scheduled.service.TaskSqlConfigService;
import com.mattoid.scheduled.task.SqlExecutor;
import com.mattoid.scheduled.util.TimeRangeParser;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.cp.bean.message.WxCpXmlMessage;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Component
public class WeComCommandHandler {

    private static final int PAGE_SIZE = 10;
    private static final int TOP_TASKS_COUNT = 3;

    /**
     * 通过数据源名称查询的前缀，例如："数据源：销售库 上个月销售额前 10 的门店"。
     */
    private static final Pattern DATASOURCE_PREFIX_PATTERN = Pattern.compile("^数据源\\s*[:：]\\s*(\\S+)(?:\\s+(.*))?$", Pattern.DOTALL);

    private final TaskConfigService taskConfigService;
    private final TaskExecutionService taskExecutionService;
    private final TaskLogMapper taskLogMapper;
    private final AiAssistantService aiAssistantService;
    private final SqlExecutor sqlExecutor;
    private final TaskSqlConfigService taskSqlConfigService;
    private final ChartGenerationService chartGenerationService;
    private final DatasourceConfigService datasourceConfigService;
    private final AiKnowledgeDocService aiKnowledgeDocService;
    private final AiConversationService aiConversationService;

    public WeComCommandHandler(TaskConfigService taskConfigService,
                               TaskExecutionService taskExecutionService,
                               TaskLogMapper taskLogMapper,
                               AiAssistantService aiAssistantService,
                               SqlExecutor sqlExecutor,
                               TaskSqlConfigService taskSqlConfigService,
                               ChartGenerationService chartGenerationService,
                               DatasourceConfigService datasourceConfigService,
                               AiKnowledgeDocService aiKnowledgeDocService,
                               AiConversationService aiConversationService) {
        this.taskConfigService = taskConfigService;
        this.taskExecutionService = taskExecutionService;
        this.taskLogMapper = taskLogMapper;
        this.aiAssistantService = aiAssistantService;
        this.sqlExecutor = sqlExecutor;
        this.taskSqlConfigService = taskSqlConfigService;
        this.chartGenerationService = chartGenerationService;
        this.datasourceConfigService = datasourceConfigService;
        this.aiKnowledgeDocService = aiKnowledgeDocService;
        this.aiConversationService = aiConversationService;
    }

    public CommandResult handle(WxCpXmlMessage message, Long configId) {
        String content = message.getContent();
        String eventKey = message.getEventKey();

        if (!StringUtils.hasText(content) && !StringUtils.hasText(eventKey)) {
            return new CommandResult("无法识别指令，请发送\"帮助\"查看可用指令。");
        }

        String command = StringUtils.hasText(eventKey) ? eventKey.trim() : content.trim();
        CommandResult result = processCommand(command);
        if (result != null && result.hasText() && result.getText().startsWith("未知指令")) {
            return handleAiFallback(command, message.getFromUserName());
        }
        return result;
    }

    /**
     * 纯文本指令处理（用于智能机器人长链模式）
     */
    public CommandResult handleText(String content) {
        return handleText(content, null);
    }

    /**
     * 纯文本指令处理（用于智能机器人长链模式），fromUser 用于构建每用户独立的会话上下文，
     * 使追问、换图表等连续对话在企业微信中同样生效。
     */
    public CommandResult handleText(String content, String fromUser) {
        if (!StringUtils.hasText(content)) {
            return new CommandResult("无法识别指令，请发送\"帮助\"查看可用指令。");
        }
        String command = content.trim();
        CommandResult result = processCommand(command);
        if (result != null && result.hasText() && result.getText().startsWith("未知指令")) {
            return handleAiFallback(command, fromUser);
        }
        return result;
    }

    private CommandResult processCommand(String command) {
        try {
            if ("QUERY_TASKS".equalsIgnoreCase(command) || command.startsWith("查询任务") || "任务列表".equals(command)) {
                return new CommandResult(handleQueryTasks(command));
            }
            if (command.startsWith("查看任务")) {
                return new CommandResult(handleViewTask(command));
            }
            if (command.startsWith("任务日志")) {
                return new CommandResult(handleTaskLogs(command));
            }
            if ("最近任务".equals(command) || "RECENT_TASKS".equalsIgnoreCase(command)) {
                return new CommandResult(handleRecentTasks());
            }
            if (command.startsWith("RUN_TASK_")) {
                return new CommandResult(handleQuickRun(command));
            }
            if (command.startsWith("运行 ") || command.startsWith("运行任务 ") || command.startsWith("运行")) {
                return new CommandResult(handleRunTask(command));
            }
            if (command.startsWith("创建任务 ")) {
                return new CommandResult(handleCreateTask(command));
            }
            if ("RUN_TASK_PROMPT".equalsIgnoreCase(command)) {
                return new CommandResult("请回复：运行 {任务ID 或 任务名称}\n例如：运行 1\n例如：运行销售日报任务");
            }
            if ("HELP".equalsIgnoreCase(command) || "帮助".equals(command)) {
                return new CommandResult(helpText());
            }
            return new CommandResult("未知指令：" + command + "\n请发送\"帮助\"查看可用指令。");
        } catch (Exception e) {
            log.error("企业微信指令处理失败: {}", command, e);
            return new CommandResult("指令处理失败: " + e.getMessage());
        }
    }

    private CommandResult handleAiFallback(String content, String fromUser) {
        try {
            String sessionId = wecomSession(fromUser);

            // 1) 显式「数据源：名称 问题」前缀：按名称解析数据源后走 schema-doc SQL 流水线（与 Web 同款）。
            DatasourcePrefix prefix = extractDatasourcePrefix(content);
            if (prefix != null) {
                if (!StringUtils.hasText(prefix.question())) {
                    return new CommandResult("请在数据源名称后补充要查询的问题，例如：数据源：" + prefix.name() + " 上个月销售额前 10 的门店");
                }
                return queryByDatasourceName(prefix.name(), prefix.question(), sessionId);
            }

            // 2) 任务管理类意图保持原行为（查看/触发/日志/创建任务）。
            IntentResult intent = aiAssistantService.parseIntent(content);
            if (intent != null && intent.isRecognized()) {
                String action = intent.getAction();
                if (isTaskIntent(action)) {
                    return executeIntent(intent, content);
                }
                if ("QUERY_DATA".equalsIgnoreCase(action)) {
                    // 命中预置 SQL（管理后台配置的 TaskSqlConfig）则沿用原逻辑；未命中则落到下方的默认数据源流水线。
                    CommandResult preconfigured = handleQueryDataIntent(intent.getParams(), content, sessionId);
                    if (preconfigured != null) {
                        return preconfigured;
                    }
                }
            }

            // 3) 其它自由文本：统一走 Web 同款 schema-doc SQL 流水线（WECOM 渠道：纯文本、绝不回显 SQL），
            //    由 AiConversationService 内部判断是数据查询还是寒暄闲聊。
            return queryByDefaultDatasource(content, sessionId);
        } catch (Exception e) {
            log.error("AI 分析企业微信消息失败: {}", content, e);
            return new CommandResult("AI 处理失败，请稍后再试。");
        }
    }

    private boolean isTaskIntent(String action) {
        return "VIEW_TASKS".equalsIgnoreCase(action)
                || "TRIGGER_TASK".equalsIgnoreCase(action)
                || "VIEW_LOGS".equalsIgnoreCase(action)
                || "CREATE_TASK".equalsIgnoreCase(action);
    }

    private CommandResult executeIntent(IntentResult intent, String originalContent) {
        String action = intent.getAction();
        Map<String, String> params = intent.getParams();
        if (!StringUtils.hasText(action)) {
            return queryByDefaultDatasource(originalContent, wecomSession(null));
        }
        try {
            return switch (action) {
                case "VIEW_TASKS" -> new CommandResult(handleViewTasksIntent(params));
                case "TRIGGER_TASK" -> new CommandResult(handleTriggerTaskIntent(params));
                case "VIEW_LOGS" -> new CommandResult(handleViewLogsIntent(params));
                case "CREATE_TASK" -> handleCreateTaskIntent(params);
                default -> queryByDefaultDatasource(originalContent, wecomSession(null));
            };
        } catch (Exception e) {
            log.error("AI 意图执行失败: action={}, params={}", action, params, e);
            return new CommandResult("指令执行失败: " + e.getMessage());
        }
    }

    private String handleViewTasksIntent(Map<String, String> params) {
        String keyword = params.get("keyword");
        if (StringUtils.hasText(keyword)) {
            var query = taskConfigService.lambdaQuery()
                    .like(TaskConfig::getTaskName, keyword);
            String status = params.get("status");
            if (StringUtils.hasText(status)) {
                query.eq(TaskConfig::getStatus, status);
            }
            List<TaskConfig> tasks = query.orderByDesc(TaskConfig::getCreateTime).list();
            if (tasks.isEmpty()) {
                return "未找到匹配的任务: " + keyword;
            }
            StringBuilder sb = new StringBuilder();
            sb.append("找到 ").append(tasks.size()).append(" 个匹配任务：\n");
            for (TaskConfig task : tasks) {
                sb.append(task.getId()).append(". ")
                        .append(task.getTaskName()).append(" [")
                        .append(task.getStatus()).append("]\n");
            }
            sb.append("\n回复\"运行 {ID 或 任务名称}\"触发任务。");
            return sb.toString();
        }
        return handleQueryTasks("任务列表");
    }

    private String handleTriggerTaskIntent(Map<String, String> params) {
        String taskId = params.get("taskId");
        String taskName = params.get("taskName");
        Map<String, Object> queryParams = buildTimeRangeParams(params.get("timeRange"));
        if (StringUtils.hasText(taskId)) {
            taskExecutionService.executeTaskAsync(Long.parseLong(taskId), "MANUAL", queryParams);
            return "已触发任务 ID: " + taskId + timeRangeHint(queryParams);
        }
        if (StringUtils.hasText(taskName)) {
            return handleRunTask("运行 " + taskName + (params.containsKey("timeRange") ? " " + params.get("timeRange") : ""));
        }
        return "请指定任务 ID 或任务名称。";
    }

    private Map<String, Object> buildTimeRangeParams(String timeRange) {
        if (!StringUtils.hasText(timeRange)) {
            return java.util.Collections.emptyMap();
        }
        Map<String, String> range = TimeRangeParser.parse(timeRange);
        if (!range.containsKey("startTime")) {
            return java.util.Collections.emptyMap();
        }
        Map<String, Object> params = new HashMap<>();
        params.put("startTime", range.get("startTime"));
        params.put("endTime", range.get("endTime"));
        return params;
    }

    private String timeRangeHint(Map<String, Object> params) {
        if (!params.containsKey("startTime")) {
            return "";
        }
        return "（时间范围：" + params.get("startTime") + " ~ " + params.get("endTime") + "）";
    }

    private String handleViewLogsIntent(Map<String, String> params) {
        String taskId = params.get("taskId");
        if (StringUtils.hasText(taskId)) {
            return handleTaskLogs("任务日志 " + taskId);
        }
        return "请指定任务 ID 查看日志。";
    }

    private CommandResult handleCreateTaskIntent(Map<String, String> params) {
        String taskName = params.get("taskName");
        String triggerConfig = params.get("triggerConfig");
        if (!StringUtils.hasText(taskName) || !StringUtils.hasText(triggerConfig)) {
            return new CommandResult("创建任务需要任务名称和触发配置，例如：\n创建每天上午9点运行的销售日报任务，CRON 表达式 0 0 9 * * ?");
        }
        String triggerType = params.get("triggerType");
        if (!StringUtils.hasText(triggerType)) {
            triggerType = triggerConfig.trim().contains(" ") ? "CRON" : "ONCE";
        }
        List<String> sqlCodes = new ArrayList<>();
        String sqlCodesStr = params.get("sqlCodes");
        if (StringUtils.hasText(sqlCodesStr)) {
            try {
                sqlCodes = Arrays.stream(sqlCodesStr.split(","))
                        .map(String::trim)
                        .filter(StringUtils::hasText)
                        .collect(Collectors.toList());
            } catch (NumberFormatException e) {
                return new CommandResult("SQL 编码格式错误，请使用逗号分隔的 SQL 编码。");
            }
        }

        try {
            TaskConfig task = new TaskConfig();
            task.setTaskName(taskName.trim());
            task.setTaskCode("TASK_" + System.currentTimeMillis());
            task.setTriggerType(triggerType.toUpperCase());
            task.setTriggerConfig(triggerConfig.trim());
            task.setStatus("ENABLE");
            taskConfigService.saveOrUpdateTask(task, sqlCodes, null);
            return new CommandResult("任务创建成功: " + task.getTaskName() + " (ID: " + task.getId() + ")");
        } catch (Exception e) {
            log.error("AI 创建任务失败: params={}", params, e);
            return new CommandResult("创建任务失败: " + e.getMessage());
        }
    }

    /**
     * 处理 QUERY_DATA 意图：优先按 AI 识别的数据源走 schema-doc 流水线；否则尝试匹配管理后台预置 SQL。
     * 返回 null 表示「没有命中任何预置 SQL 且未指定数据源」，由调用方回退到默认数据源的 schema-doc 流水线。
     */
    private CommandResult handleQueryDataIntent(Map<String, String> params, String originalContent, String sessionId) {
        // 当 AI 意图识别出数据源名称时，直接在该数据源上按自然语言生成并执行 SQL（Web 同款，纯文本、不回显 SQL）。
        String dsName = params != null ? params.get("datasource") : null;
        if (StringUtils.hasText(dsName)) {
            return queryByDatasourceName(dsName.trim(), originalContent, sessionId);
        }

        String keyword = params.get("keyword");
        if (!StringUtils.hasText(keyword)) {
            keyword = originalContent;
        }

        List<TaskSqlConfig> candidates = findMatchingSqlConfigs(keyword);
        if (candidates.isEmpty()) {
            // 不再直接提示「未找到 SQL 配置」，而是让调用方回退到默认数据源的 schema-doc 流水线（与 Web 一致）。
            return null;
        }
        if (candidates.size() > 1) {
            StringBuilder sb = new StringBuilder("找到多个相关 SQL，请指定更精确的关键词：\n");
            for (TaskSqlConfig config : candidates) {
                sb.append(config.getId()).append(". ").append(config.getSqlName());
                if (StringUtils.hasText(config.getDescription())) {
                    sb.append(" (").append(config.getDescription()).append(")");
                }
                sb.append("\n");
            }
            return new CommandResult(sb.toString());
        }

        TaskSqlConfig config = candidates.get(0);
        Map<String, String> queryParams = aiAssistantService.extractQueryParams(originalContent);
        // 将 AI 意图中的已知参数合并进来
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!"keyword".equalsIgnoreCase(entry.getKey()) && !"chartType".equalsIgnoreCase(entry.getKey())) {
                queryParams.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }

        try {
            List<Map<String, Object>> data = sqlExecutor.executeQuery(config.getDatasourceId(), config.getSqlContent(), new HashMap<>(queryParams));
            if (data.isEmpty()) {
                return new CommandResult("查询完成，未返回数据。\nSQL: " + config.getSqlName());
            }

            String chartType = params.get("chartType");
            File chartFile = null;
            if (StringUtils.hasText(chartType)) {
                chartFile = chartGenerationService.generateChart(data, chartType, config.getSqlName());
            }

            String summary = buildDataSummary(config, data, queryParams);
            return new CommandResult(summary, chartFile);
        } catch (Exception e) {
            log.error("SQL 查询失败: sqlId={}, sqlName={}", config.getId(), config.getSqlName(), e);
            return new CommandResult("查询失败: " + e.getMessage() + "\nSQL: " + config.getSqlName());
        }
    }

    /**
     * 按数据源名称解析后，走 Web 同款 schema-doc SQL 流水线（WECOM 渠道：纯文本、绝不回显 SQL）。
     * 名称未匹配到唯一数据源时返回引导文案，由用户重试。
     */
    private CommandResult queryByDatasourceName(String dsName, String question, String sessionId) {
        if (!StringUtils.hasText(dsName)) {
            return new CommandResult("请指定数据源名称，例如：数据源：销售库 查询上个月销售额前 10 的门店");
        }
        List<DatasourceConfig> matches = findDatasourcesByName(dsName);
        if (matches.isEmpty()) {
            return new CommandResult("未找到名称包含 \"" + dsName + "\" 的已启用数据源，请检查名称或先在管理后台创建并启用数据源。");
        }
        if (matches.size() > 1) {
            StringBuilder sb = new StringBuilder("找到多个匹配的数据源，请使用更精确的名称：\n");
            for (DatasourceConfig ds : matches) {
                sb.append(ds.getId()).append(". ").append(ds.getName());
                if (StringUtils.hasText(ds.getDatabaseName())) {
                    sb.append(" (").append(ds.getDatabaseName()).append(")");
                }
                sb.append("\n");
            }
            return new CommandResult(sb.toString());
        }
        return queryViaSchema(sessionId, matches.get(0).getId(), question);
    }

    /**
     * 使用默认数据源（第一个已启用且已同步数据字典的数据源）走 Web 同款 schema-doc SQL 流水线。
     * 当没有任何已启用数据源时回退到通用对话；当数据源已启用但未同步数据字典时，
     * 由 {@link AiConversationService} 给出「请先同步表结构」的明确引导。
     */
    private CommandResult queryByDefaultDatasource(String question, String sessionId) {
        Long dsId = resolveDefaultDatasourceId();
        return queryViaSchema(sessionId, dsId, question);
    }

    private CommandResult queryViaSchema(String sessionId, Long datasourceId, String question) {
        AiConversationService.ChatReplyResult result = aiConversationService.chatAndPersist(
                sessionId, datasourceId, question, AiConversationService.ReplyChannel.WECOM);
        return new CommandResult(result.text(), result.imageFile());
    }

    /**
     * 选择默认数据源：优先返回「已启用且已生成 SCHEMA 数据字典」的第一个数据源（按 ID 升序）；
     * 若都已启用但未生成字典，则返回第一个已启用数据源（让上层提示用户去同步）；
     * 没有任何已启用数据源时返回 null（回退到通用对话）。
     */
    private Long resolveDefaultDatasourceId() {
        List<DatasourceConfig> enabled = datasourceConfigService.lambdaQuery()
                .eq(DatasourceConfig::getStatus, 1)
                .orderByAsc(DatasourceConfig::getId)
                .list();
        if (enabled.isEmpty()) {
            return null;
        }
        for (DatasourceConfig ds : enabled) {
            if (aiKnowledgeDocService.getLatestByDatasource(ds.getId(), "SCHEMA") != null) {
                return ds.getId();
            }
        }
        return enabled.get(0).getId();
    }

    /** 企业微信每用户独立会话：fromUser 缺失时退化为共享会话（长链/异常场景兜底）。 */
    private String wecomSession(String fromUser) {
        return StringUtils.hasText(fromUser) ? "wecom:" + fromUser : "wecom:anonymous";
    }

    private DatasourcePrefix extractDatasourcePrefix(String content) {
        if (!StringUtils.hasText(content)) {
            return null;
        }
        Matcher matcher = DATASOURCE_PREFIX_PATTERN.matcher(content.trim());
        if (!matcher.matches()) {
            return null;
        }
        return new DatasourcePrefix(matcher.group(1), matcher.group(2));
    }

    private record DatasourcePrefix(String name, String question) {
    }

    private List<DatasourceConfig> findDatasourcesByName(String name) {
        if (!StringUtils.hasText(name)) {
            return Collections.emptyList();
        }
        String clean = name.replaceAll("[\\p{P}\\s]+", "").trim();
        List<DatasourceConfig> all = datasourceConfigService.lambdaQuery()
                .eq(DatasourceConfig::getStatus, 1)
                .list();

        List<DatasourceConfig> exact = new ArrayList<>();
        for (DatasourceConfig ds : all) {
            String dsName = ds.getName();
            if (dsName == null) {
                continue;
            }
            String dsClean = dsName.replaceAll("[\\p{P}\\s]+", "").trim();
            if (name.equalsIgnoreCase(dsName) || clean.equalsIgnoreCase(dsClean)) {
                exact.add(ds);
            }
        }
        if (!exact.isEmpty()) {
            return exact;
        }

        List<DatasourceConfig> fuzzy = new ArrayList<>();
        for (DatasourceConfig ds : all) {
            String dsName = ds.getName();
            if (dsName == null) {
                continue;
            }
            String dsClean = dsName.replaceAll("[\\p{P}\\s]+", "").trim();
            if (containsIgnoreCase(dsName, name) || containsIgnoreCase(dsClean, clean)) {
                fuzzy.add(ds);
            }
        }
        return fuzzy;
    }

    private List<TaskSqlConfig> findMatchingSqlConfigs(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return Collections.emptyList();
        }
        String clean = keyword.replaceAll("[\\p{P}\\s]+", "").trim();
        List<TaskSqlConfig> all = taskSqlConfigService.lambdaQuery()
                .eq(TaskSqlConfig::getStatus, 1)
                .list();
        List<TaskSqlConfig> matches = new ArrayList<>();
        for (TaskSqlConfig config : all) {
            if (containsIgnoreCase(config.getSqlName(), keyword)
                    || containsIgnoreCase(config.getSqlName(), clean)
                    || containsIgnoreCase(config.getDescription(), keyword)
                    || containsIgnoreCase(config.getDescription(), clean)
                    || containsIgnoreCase(config.getSqlContent(), keyword)
                    || containsIgnoreCase(config.getSqlContent(), clean)) {
                matches.add(config);
            }
        }
        return matches;
    }

    private boolean containsIgnoreCase(String source, String target) {
        if (!StringUtils.hasText(source) || !StringUtils.hasText(target)) {
            return false;
        }
        return source.toLowerCase().contains(target.toLowerCase());
    }

    private String buildDataSummary(TaskSqlConfig config, List<Map<String, Object>> data, Map<String, String> queryParams) {
        StringBuilder sb = new StringBuilder();
        sb.append("查询结果\n");
        sb.append("SQL: ").append(config.getSqlName()).append("\n");
        if (!queryParams.isEmpty()) {
            sb.append("参数: ").append(queryParams.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining(", "))).append("\n");
        }
        sb.append("行数: ").append(data.size()).append("\n");

        int previewLimit = Math.min(data.size(), 10);
        List<String> columns = new ArrayList<>(data.get(0).keySet());
        sb.append("预览（前 ").append(previewLimit).append(" 行）:\n");
        for (int i = 0; i < previewLimit; i++) {
            Map<String, Object> row = data.get(i);
            for (String col : columns) {
                sb.append(col).append(":").append(row.get(col)).append("  ");
            }
            sb.append("\n");
        }
        if (data.size() > previewLimit) {
            sb.append("...");
        }
        return sb.toString();
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

        TimeRangeResult timeResult = extractTimeRange(arg);
        String taskArg = timeResult.taskArg();
        Map<String, Object> params = timeResult.params();

        TaskConfig task;
        if (taskArg.matches("\\d+")) {
            Long taskId = Long.parseLong(taskArg);
            task = taskConfigService.getById(taskId);
            if (task == null) {
                return "任务不存在: " + taskId;
            }
        } else {
            String name = taskArg;
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

        taskExecutionService.executeTaskAsync(task.getId(), "MANUAL", params);
        String timeHint = params.containsKey("startTime")
                ? "（时间范围：" + params.get("startTime") + " ~ " + params.get("endTime") + "）"
                : "";
        return "已触发任务: " + task.getTaskName() + " (ID: " + task.getId() + ")" + timeHint + "，请稍后查看执行结果。";
    }

    private TimeRangeResult extractTimeRange(String arg) {
        String[] tokens = arg.split("\\s+");
        int maxSuffix = Math.min(tokens.length, 5);
        for (int suffixLen = 1; suffixLen <= maxSuffix; suffixLen++) {
            int startIdx = tokens.length - suffixLen;
            String suffix = String.join(" ", Arrays.copyOfRange(tokens, startIdx, tokens.length));
            String prefix = startIdx > 0
                    ? String.join(" ", Arrays.copyOfRange(tokens, 0, startIdx)).trim()
                    : "";
            Map<String, String> range = TimeRangeParser.parse(suffix);
            if (range.containsKey("startTime") && StringUtils.hasText(prefix)) {
                Map<String, Object> params = new HashMap<>();
                params.put("startTime", range.get("startTime"));
                params.put("endTime", range.get("endTime"));
                return new TimeRangeResult(prefix, params);
            }
        }
        return new TimeRangeResult(arg, java.util.Collections.emptyMap());
    }

    private record TimeRangeResult(String taskArg, Map<String, Object> params) {
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
        List<String> sqlCodes = Arrays.stream(parts[3].split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
        if (sqlCodes.isEmpty()) {
            return "请至少指定一个 SQL 编码。";
        }

        TaskConfig task = new TaskConfig();
        task.setTaskName(taskName);
        task.setTaskCode(taskCode);
        task.setTriggerType(triggerConfig.contains(" ") ? "CRON" : "ONCE");
        task.setTriggerConfig(triggerConfig);
        task.setStatus("ENABLE");
        taskConfigService.saveOrUpdateTask(task, sqlCodes, null);
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
                "运行 {任务名称} {时间范围} - 按指定时间范围运行任务，例如：运行 销售日报 昨天\n" +
                "查询 {数据关键词} [折线图/柱状图/饼状图] - 直接查询预置 SQL 数据\n" +
                "数据源：{数据源名称} {查询问题} - 在指定数据源中按自然语言查询（需先同步数据字典），例如：数据源：销售库 上个月销售额前 10 的门店\n" +
                "{任意数据问题} - 直接用自然语言向默认数据源提问（需先同步数据字典），例如：上个月 道达尔 有多少人进行过点赞\n" +
                "创建任务 任务名|任务编码|CRON表达式|sqlId1,sqlId2 - 创建任务";
    }
}