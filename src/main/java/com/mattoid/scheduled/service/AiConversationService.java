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
            2. 如果结果是数据表格，请用 Markdown 表格展示全部或关键数据，并在表格前用一句话概括。
            3. 如果结果是单值或汇总统计，直接给出结论，必要时可列出关键指标。
            4. 不要重复 SQL。
            5. 如果执行失败或没有数据，请说明原因并给出建议。
            """;

    private static final String SYSTEM_PROMPT_SQL_SUMMARY_WECOM = """
            你是一名数据分析助手。请根据用户的原始问题、生成的 SQL 以及执行结果，给出简洁、准确的中文回复。
            回复要求：
            1. 使用纯文本回复，不要使用 Markdown、表格、图片等富文本格式。
            2. 如果结果是数据表格，请用文字分行列出关键数据，每行一个记录，字段用冒号分隔。
            3. 如果结果是单值或汇总统计，直接给出结论。
            4. 不要重复 SQL。
            5. 如果执行失败或没有数据，请说明原因并给出建议。
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
        SqlGenerateResult sqlResult = aiAssistantService.generateSql(schemaDoc, userMessage, history);
        if (!StringUtils.hasText(sqlResult.getSql())) {
            return new ChatReplyResult("未能根据数据字典生成 SQL：" + sqlResult.getExplanation());
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
            return new ChatReplyResult("SQL 执行失败：" + e.getMessage() + "\n\nSQL：" + sqlResult.getSql());
        }

        if (StringUtils.hasText(sqlResult.getChartType()) && !rows.isEmpty()) {
            File chartFile = generateChartFile(rows, sqlResult.getChartType(), sqlResult.getChartTitle());
            if (chartFile != null) {
                String chartUrl = saveChartFile(chartFile);
                if (chartUrl != null) {
                    String text = channel == ReplyChannel.WECOM
                            ? "已为您生成图表，请查看图片。"
                            : "已为您生成图表：\n\n![" + (sqlResult.getChartTitle() != null ? sqlResult.getChartTitle() : "数据图表") + "](" + chartUrl + ")";
                    return new ChatReplyResult(text, chartFile);
                }
            }
            executeInfo += "（图表生成失败，已返回数据摘要）";
        }

        return new ChatReplyResult(summarizeSqlResult(userMessage, sqlResult, rows, executeInfo, history, channel));
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
            return executeInfo + "\n\nSQL：" + sqlResult.getSql() + "\n\n结果：\n" + JSON.toJSONString(rows);
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
            return executeInfo + "\n\nSQL：" + sqlResult.getSql() + "\n\n结果：\n" + JSON.toJSONString(rows);
        }
        return response.getContent();
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
