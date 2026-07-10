package com.mattoid.scheduled.service;

import com.alibaba.fastjson2.JSON;
import com.mattoid.scheduled.ai.AiChatRequest;
import com.mattoid.scheduled.ai.AiChatResponse;
import com.mattoid.scheduled.ai.AiClient;
import com.mattoid.scheduled.ai.AiClientFactory;
import com.mattoid.scheduled.ai.AiMessage;
import com.mattoid.scheduled.dto.SqlGenerateResult;
import com.mattoid.scheduled.entity.AiConfig;
import com.mattoid.scheduled.entity.AiConversation;
import com.mattoid.scheduled.entity.AiKnowledgeDoc;
import com.mattoid.scheduled.mapper.AiConversationMapper;
import com.mattoid.scheduled.task.SqlExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class AiConversationService {

    private static final String SYSTEM_PROMPT_SQL_SUMMARY_WEB = """
            你是一名数据分析助手。请根据用户的原始问题、生成的 SQL 以及执行结果，给出简洁、准确的中文回复。
            回复要求：
            1. 使用 Markdown 格式回复。
            2. 默认情况下（用户没有明确要求图表、文字榜单等其他形式），只要结果是多行多列的数据，就用 Markdown 表格展示；列较多时列出关键列，并在表格前用一句话概括结论。
            3. 如果结果是单值或汇总统计，直接给出结论，必要时列出关键指标。
            4. 严禁在回复中出现 SQL 语句、SQL 代码块或对 SQL 的解释说明；SQL 会由系统在回复末尾单独折叠展示，你只负责总结结果。
            5. 如果执行失败或没有数据，请说明原因并给出建议，同样不要贴出 SQL。
            """;

    private static final String SYSTEM_PROMPT_SQL_SUMMARY_WECOM = """
            你是一名数据分析助手。请根据用户的原始问题、生成的 SQL 以及执行结果，给出简洁、准确的中文回复。
            回复要求：
            1. 使用纯文本回复，不要使用 Markdown、表格、图片等富文本格式。
            2. 如果结果是数据表格，请用文字分行列出关键数据，每行一个记录，字段用冒号分隔。
            3. 如果结果是单值或汇总统计，直接给出结论。
            4. 严禁在回复中出现 SQL 语句或对 SQL 的解释；SQL 不会发给企业微信用户，你只需总结结果。
            5. 如果执行失败或没有数据，请说明原因并给出建议，同样不要贴出 SQL。
            """;

    public enum ReplyChannel {
        WEB, WECOM
    }

    public record ChatReplyResult(String text, File imageFile) {
        public ChatReplyResult(String text) {
            this(text, null);
        }
    }

    private final AiConversationMapper aiConversationMapper;
    private final AiKnowledgeDocService aiKnowledgeDocService;
    private final AiAssistantService aiAssistantService;
    private final AiConfigService aiConfigService;
    private final AiClientFactory aiClientFactory;
    private final SqlExecutor sqlExecutor;
    private final ChartGenerationService chartGenerationService;

    @Value("${report.upload.path:${user.home}/scheduled-task/uploads}")
    private String uploadPath;

    public AiConversationService(AiConversationMapper aiConversationMapper,
                                 AiKnowledgeDocService aiKnowledgeDocService,
                                 AiAssistantService aiAssistantService,
                                 AiConfigService aiConfigService,
                                 AiClientFactory aiClientFactory,
                                 SqlExecutor sqlExecutor,
                                 ChartGenerationService chartGenerationService) {
        this.aiConversationMapper = aiConversationMapper;
        this.aiKnowledgeDocService = aiKnowledgeDocService;
        this.aiAssistantService = aiAssistantService;
        this.aiConfigService = aiConfigService;
        this.aiClientFactory = aiClientFactory;
        this.sqlExecutor = sqlExecutor;
        this.chartGenerationService = chartGenerationService;
    }

    public AiConversation getOrCreate(String sessionId, Long datasourceId) {
        AiConversation conversation = StringUtils.hasText(sessionId)
                ? aiConversationMapper.selectOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<AiConversation>()
                                .eq(AiConversation::getSessionId, sessionId))
                : null;
        if (conversation != null) {
            // 允许在已有会话中绑定或切换数据源：自动重新解析该数据源最新的 SCHEMA 文档，
            // 这样「先创建会话、后选择数据源」或「会话中途切换数据源」都能正确进入 SQL 生成流程。
            if (datasourceId != null && !datasourceId.equals(conversation.getDatasourceId())) {
                conversation.setDatasourceId(datasourceId);
                AiKnowledgeDoc doc = aiKnowledgeDocService.getLatestByDatasource(datasourceId, "SCHEMA");
                conversation.setDocId(doc != null ? doc.getId() : null);
                aiConversationMapper.updateById(conversation);
            }
            return conversation;
        }
        conversation = new AiConversation();
        conversation.setSessionId(StringUtils.hasText(sessionId) ? sessionId : UUID.randomUUID().toString());
        conversation.setDatasourceId(datasourceId);
        if (datasourceId != null) {
            AiKnowledgeDoc doc = aiKnowledgeDocService.getLatestByDatasource(datasourceId, "SCHEMA");
            conversation.setDocId(doc != null ? doc.getId() : null);
        }
        conversation.setMessages("[]");
        conversation.setStatus(1);
        aiConversationMapper.insert(conversation);
        return conversation;
    }

    public AiConversation chat(String sessionId, Long datasourceId, String userMessage) {
        return chat(sessionId, datasourceId, userMessage, ReplyChannel.WEB);
    }

    public AiConversation chat(String sessionId, Long datasourceId, String userMessage, ReplyChannel channel) {
        ChatReplyResult result = chatWithResult(sessionId, datasourceId, userMessage, channel);
        AiConversation conversation = getOrCreate(sessionId, datasourceId);
        List<AiMessage> messages = loadMessages(conversation);
        messages.add(AiMessage.user(userMessage));
        messages.add(AiMessage.assistant(result.text()));
        conversation.setMessages(JSON.toJSONString(messages));
        aiConversationMapper.updateById(conversation);
        return conversation;
    }

    public ChatReplyResult chatWithResult(String sessionId, Long datasourceId, String userMessage, ReplyChannel channel) {
        AiConversation conversation = getOrCreate(sessionId, datasourceId);
        List<AiMessage> messages = loadMessages(conversation);
        messages.add(AiMessage.user(userMessage));

        String schemaDoc = loadSchemaDoc(conversation);
        ChatReplyResult result;
        if (conversation.getDatasourceId() != null) {
            if (StringUtils.hasText(schemaDoc)) {
                result = handleSqlChat(conversation.getDatasourceId(), schemaDoc, userMessage, messages, channel);
            } else {
                // 已绑定数据源但尚未生成数据字典：明确引导用户去同步，而不是降级成泛泛闲聊。
                result = new ChatReplyResult("当前数据源尚未生成数据字典文档，无法据此生成 SQL。"
                        + "请先在「数据源管理」中对该数据源执行「同步表结构」并生成数据字典后重试。");
            }
        } else {
            result = new ChatReplyResult(handleGenericChat(userMessage, messages, channel));
        }
        return result;
    }

    private ChatReplyResult handleSqlChat(Long datasourceId, String schemaDoc, String userMessage, List<AiMessage> messages, ReplyChannel channel) {
        List<AiMessage> history = messages.size() > 1 ? messages.subList(0, messages.size() - 1) : java.util.Collections.emptyList();
        // 默认每次查询独立、不带上下文，避免无关问题之间相互污染；
        // 仅当用户话像是承接上文（追问/补条件/换图表等）时才把历史喂给模型，防止记忆丢失。
        List<AiMessage> aiHistory = isContextDependent(userMessage) ? history : java.util.Collections.emptyList();
        SqlGenerateResult sqlResult = aiAssistantService.generateSql(schemaDoc, userMessage, aiHistory);
        if (!StringUtils.hasText(sqlResult.getSql())) {
            // AI 判断用户没有提出数据查询需求（寒暄、闲聊、咨询功能等），回退到通用对话。
            return new ChatReplyResult(handleGenericChat(userMessage, messages, channel));
        }

        List<Map<String, Object>> rows;
        String executeInfo;
        try {
            SqlExecutor.validateReadOnlySql(sqlResult.getSql());
            Map<String, Object> params = new HashMap<>(sqlResult.getParams());
            rows = sqlExecutor.executeQuery(datasourceId, sqlResult.getSql(), params);
            executeInfo = "查询成功，返回 " + rows.size() + " 条数据。";
        } catch (Exception e) {
            log.error("AI 生成 SQL 执行失败: {}", sqlResult.getSql(), e);
            // 失败原因放在对话正文，SQL 仅放入 Web 端折叠块，避免正文中泄露 SQL。
            return new ChatReplyResult(appendSqlBlock("SQL 执行失败：" + e.getMessage(), sqlResult.getSql(), channel));
        }

        if (StringUtils.hasText(sqlResult.getChartType()) && !rows.isEmpty()) {
            File chartFile = generateChartFile(rows, sqlResult.getChartType(), sqlResult.getChartTitle());
            if (chartFile != null) {
                String chartUrl = saveChartFile(chartFile);
                if (chartUrl != null) {
                    String text = channel == ReplyChannel.WECOM
                            ? "已为您生成图表，请查看图片。"
                            : "已为您生成图表：\n\n![" + (sqlResult.getChartTitle() != null ? sqlResult.getChartTitle() : "数据图表") + "](" + chartUrl + ")";
                    return new ChatReplyResult(appendSqlBlock(text, sqlResult.getSql(), channel), chartFile);
                }
            }
            executeInfo += "（图表生成失败，已返回数据摘要）";
        }

        String summary = summarizeSqlResult(userMessage, sqlResult, rows, executeInfo, aiHistory, channel);
        return new ChatReplyResult(appendSqlBlock(summary, sqlResult.getSql(), channel));
    }

    /**
     * Web 端在回复末尾追加一段默认折叠、点击展开的 SQL 代码块；
     * 企业微信等纯文本渠道不追加，避免 Markdown/富文本无法展示。
     * 这里直接输出 HTML（而非 Markdown 代码围栏），是因为 marked 不会解析 HTML 块内部的围栏语法；
     * SQL 中的 &lt;、&gt;、&amp; 需转义以免破坏 HTML 结构。
     */
    private String appendSqlBlock(String text, String sql, ReplyChannel channel) {
        if (channel != ReplyChannel.WEB || !StringUtils.hasText(sql)) {
            return text;
        }
        return text + "\n\n<details class=\"sql-block\"><summary>执行 SQL</summary>"
                + "<pre><code class=\"language-sql\">" + escapeHtml(sql) + "</code></pre></details>";
    }

    private String escapeHtml(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * 判断一句话是否依赖上下文（追问、补充条件、切换展示等）。
     * 命中则返回 true，需要把对话历史一起发给模型；否则视为一次全新的独立查询。
     * 误判为「依赖上下文」的代价仅是带上历史（已保证 system 单条置顶，安全），
     * 因此整体策略偏向命中，优先保证不丢记忆。
     */
    private static final java.util.regex.Pattern CONTEXT_DEPENDENT_PATTERN = java.util.regex.Pattern.compile(
            "刚才|上面|上一个|前一个|上次|再|也|还|继续|再来|换个|换成|改成|换种|加上|去掉|不要|排除|只看|只查|排序|升序|降序|导出|饼图|柱状图|折线图|呢$");

    private boolean isContextDependent(String userMessage) {
        return StringUtils.hasText(userMessage) && CONTEXT_DEPENDENT_PATTERN.matcher(userMessage).find();
    }

    private String handleGenericChat(String userMessage, List<AiMessage> messages, ReplyChannel channel) {
        AiConfig config = aiConfigService.getDefaultConfig();
        if (config == null) {
            return "未配置默认 AI，无法回复。";
        }
        String systemPrompt = StringUtils.hasText(config.getSystemPrompt()) ? config.getSystemPrompt() : "你是智能助手。";
        if (channel == ReplyChannel.WECOM) {
            systemPrompt += " 请注意：当前回复将发送到企业微信，请使用纯文本，不要使用 Markdown、表格或图片。";
        } else {
            systemPrompt += " 请注意：当前回复将在网页对话中展示，可以使用 Markdown 格式。";
        }
        List<AiMessage> requestMessages = new ArrayList<>();
        requestMessages.add(AiMessage.system(systemPrompt));
        requestMessages.addAll(messages);

        AiClient client = aiClientFactory.createClient(config);
        AiChatResponse response = client.chat(AiChatRequest.of(config.getModel(), requestMessages));
        if (!response.isSuccess()) {
            log.error("AI 对话失败: {}", response.getErrorMessage());
            return "AI 回复失败：" + response.getErrorMessage();
        }
        return response.getContent();
    }

    private File generateChartFile(List<Map<String, Object>> rows, String chartType, String chartTitle) {
        try {
            return chartGenerationService.generateChart(rows, chartType, chartTitle);
        } catch (Exception e) {
            log.error("生成图表失败: type={}, title={}", chartType, chartTitle, e);
            return null;
        }
    }

    private String saveChartFile(File chartFile) {
        if (chartFile == null || !chartFile.exists()) {
            return null;
        }
        try {
            String dateFolder = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String fileName = UUID.randomUUID().toString().substring(0, 8) + ".png";
            Path targetDir = Paths.get(uploadPath, "ai-charts", dateFolder);
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }
            Path targetFile = targetDir.resolve(fileName);
            Files.copy(chartFile.toPath(), targetFile, StandardCopyOption.REPLACE_EXISTING);
            return "/storage/ai-charts/" + dateFolder + "/" + fileName;
        } catch (Exception e) {
            log.error("保存图表文件失败: {}", chartFile.getAbsolutePath(), e);
            return null;
        }
    }

    private String summarizeSqlResult(String userMessage, SqlGenerateResult sqlResult,
                                      List<Map<String, Object>> rows, String executeInfo,
                                      List<AiMessage> history, ReplyChannel channel) {
        AiConfig config = aiConfigService.getDefaultConfig();
        if (config == null) {
            return buildFallbackSummary(executeInfo, rows, channel);
        }

        List<AiMessage> requestMessages = new ArrayList<>();
        String systemPrompt = channel == ReplyChannel.WECOM ? SYSTEM_PROMPT_SQL_SUMMARY_WECOM : SYSTEM_PROMPT_SQL_SUMMARY_WEB;
        boolean hasHistory = history != null && !history.isEmpty();
        // 仅保留一条 system 消息并置于首位（兼容 SenseNova 等要求 system 必须在开头的厂商），
        // 历史上下文以 user/assistant 轮次形式跟在后面。
        if (hasHistory) {
            systemPrompt += "\n\n另外，以下是与当前用户的连续对话记录，总结时请结合上下文理解用户意图（如指代、延续上次查询）。";
        }
        requestMessages.add(AiMessage.system(systemPrompt));
        if (hasHistory) {
            requestMessages.addAll(history);
        }

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("用户问题：").append(userMessage).append("\n\n");
        userPrompt.append("生成的 SQL：").append(sqlResult.getSql()).append("\n\n");
        userPrompt.append("执行信息：").append(executeInfo).append("\n\n");
        userPrompt.append("执行结果（JSON）：\n").append(JSON.toJSONString(rows)).append("\n\n");
        userPrompt.append("请根据以上信息回复用户。");
        requestMessages.add(AiMessage.user(userPrompt.toString()));

        AiClient client = aiClientFactory.createClient(config);
        AiChatResponse response = client.chat(AiChatRequest.of(config.getModel(), requestMessages));
        if (!response.isSuccess()) {
            log.error("SQL 结果总结失败: {}", response.getErrorMessage());
            return buildFallbackSummary(executeInfo, rows, channel);
        }
        return response.getContent();
    }

    /**
     * AI 不可用（未配置或调用失败）时的兜底回复：只返回执行信息与数据预览，
     * 对话正文中绝不出现 SQL 或原始 JSON（SQL 由调用方通过 appendSqlBlock 单独折叠展示）。
     */
    private String buildFallbackSummary(String executeInfo, List<Map<String, Object>> rows, ReplyChannel channel) {
        if (rows == null || rows.isEmpty()) {
            return executeInfo;
        }
        String preview = channel == ReplyChannel.WECOM ? buildPlainPreview(rows, 20) : buildMarkdownTable(rows, 50);
        if (!StringUtils.hasText(preview)) {
            return executeInfo;
        }
        return executeInfo + "\n\n" + preview;
    }

    private String buildMarkdownTable(List<Map<String, Object>> rows, int maxRows) {
        if (rows == null || rows.isEmpty()) {
            return "";
        }
        List<String> columns = new ArrayList<>(rows.get(0).keySet());
        if (columns.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("| ").append(String.join(" | ", columns)).append(" |\n");
        sb.append("| ");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sb.append(" | ");
            }
            sb.append("---");
        }
        sb.append(" |\n");
        int limit = Math.min(rows.size(), maxRows);
        for (int i = 0; i < limit; i++) {
            Map<String, Object> row = rows.get(i);
            sb.append("| ");
            for (int c = 0; c < columns.size(); c++) {
                if (c > 0) {
                    sb.append(" | ");
                }
                sb.append(escapeCell(row.get(columns.get(c))));
            }
            sb.append(" |\n");
        }
        if (rows.size() > maxRows) {
            sb.append("\n（仅展示前 ").append(maxRows).append(" 条，共 ").append(rows.size()).append(" 条）");
        }
        return sb.toString();
    }

    private String buildPlainPreview(List<Map<String, Object>> rows, int maxRows) {
        if (rows == null || rows.isEmpty()) {
            return "";
        }
        List<String> columns = new ArrayList<>(rows.get(0).keySet());
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(rows.size(), maxRows);
        for (int i = 0; i < limit; i++) {
            Map<String, Object> row = rows.get(i);
            StringBuilder line = new StringBuilder();
            for (String col : columns) {
                if (line.length() > 0) {
                    line.append("，");
                }
                line.append(col).append("：").append(row.get(col) == null ? "" : row.get(col));
            }
            sb.append(line).append("\n");
        }
        if (rows.size() > maxRows) {
            sb.append("（仅展示前 ").append(maxRows).append(" 条，共 ").append(rows.size()).append(" 条）");
        }
        return sb.toString().trim();
    }

    private String escapeCell(Object value) {
        if (value == null) {
            return "";
        }
        // 转义会破坏 Markdown 表格的字符，并把换行压成空格，保持单元格单行。
        return value.toString()
                .replace("|", "\\|")
                .replace("\r", " ")
                .replace("\n", " ");
    }

    private String loadSchemaDoc(AiConversation conversation) {
        if (conversation.getDocId() != null) {
            AiKnowledgeDoc doc = aiKnowledgeDocService.getById(conversation.getDocId());
            if (doc != null) {
                return aiKnowledgeDocService.readContent(doc);
            }
        }
        if (conversation.getDatasourceId() != null) {
            AiKnowledgeDoc doc = aiKnowledgeDocService.getLatestByDatasource(conversation.getDatasourceId(), "SCHEMA");
            if (doc != null) {
                conversation.setDocId(doc.getId());
                return aiKnowledgeDocService.readContent(doc);
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<AiMessage> loadMessages(AiConversation conversation) {
        if (!StringUtils.hasText(conversation.getMessages())) {
            return new ArrayList<>();
        }
        try {
            return JSON.parseArray(conversation.getMessages(), AiMessage.class);
        } catch (Exception e) {
            log.error("解析会话消息失败: {}", conversation.getMessages(), e);
            return new ArrayList<>();
        }
    }
}
