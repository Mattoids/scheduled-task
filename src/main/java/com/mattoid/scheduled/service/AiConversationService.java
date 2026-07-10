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

    private static final String SYSTEM_PROMPT_SQL_SUMMARY = """
            你是一名数据分析助手。请根据用户的原始问题、生成的 SQL 以及执行结果，给出简洁、准确的中文回复。
            回复要求：
            1. 使用 Markdown 格式回复。
            2. 如果结果是数据表格，请用 Markdown 表格展示全部或关键数据，并在表格前用一句话概括。
            3. 如果结果是单值或汇总统计，直接给出结论，必要时可列出关键指标。
            4. 不要重复 SQL。
            5. 如果执行失败或没有数据，请说明原因并给出建议。
            """;

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
        AiConversation conversation = getOrCreate(sessionId, datasourceId);
        List<AiMessage> messages = loadMessages(conversation);

        messages.add(AiMessage.user(userMessage));

        String schemaDoc = loadSchemaDoc(conversation);
        String reply;

        if (StringUtils.hasText(schemaDoc) && conversation.getDatasourceId() != null) {
            reply = handleSqlChat(conversation.getDatasourceId(), schemaDoc, userMessage, messages);
        } else {
            reply = handleGenericChat(userMessage, messages);
        }

        messages.add(AiMessage.assistant(reply));
        conversation.setMessages(JSON.toJSONString(messages));
        aiConversationMapper.updateById(conversation);
        return conversation;
    }

    private String handleSqlChat(Long datasourceId, String schemaDoc, String userMessage, List<AiMessage> messages) {
        List<AiMessage> history = messages.size() > 1 ? messages.subList(0, messages.size() - 1) : java.util.Collections.emptyList();
        SqlGenerateResult sqlResult = aiAssistantService.generateSql(schemaDoc, userMessage, history);
        if (!StringUtils.hasText(sqlResult.getSql())) {
            return "未能根据数据字典生成 SQL：" + sqlResult.getExplanation();
        }

        List<Map<String, Object>> rows;
        String executeInfo;
        try {
            Map<String, Object> params = new HashMap<>(sqlResult.getParams());
            rows = sqlExecutor.executeQuery(datasourceId, sqlResult.getSql(), params);
            executeInfo = "查询成功，返回 " + rows.size() + " 条数据。";
        } catch (Exception e) {
            log.error("AI 生成 SQL 执行失败: {}", sqlResult.getSql(), e);
            return "生成的 SQL 执行失败：" + e.getMessage() + "\n\nSQL：" + sqlResult.getSql();
        }

        if (StringUtils.hasText(sqlResult.getChartType()) && !rows.isEmpty()) {
            String chartUrl = generateChartImage(rows, sqlResult.getChartType(), sqlResult.getChartTitle());
            if (chartUrl != null) {
                return "已为您生成图表：\n\n![" + (sqlResult.getChartTitle() != null ? sqlResult.getChartTitle() : "数据图表") + "](" + chartUrl + ")";
            }
            executeInfo += "（图表生成失败，已返回数据摘要）";
        }

        return summarizeSqlResult(userMessage, sqlResult, rows, executeInfo, history);
    }

    private String handleGenericChat(String userMessage, List<AiMessage> messages) {
        AiConfig config = aiConfigService.getDefaultConfig();
        if (config == null) {
            return "未配置默认 AI，无法回复。";
        }
        List<AiMessage> requestMessages = new ArrayList<>();
        requestMessages.add(AiMessage.system(StringUtils.hasText(config.getSystemPrompt()) ? config.getSystemPrompt() : "你是智能助手。"));
        requestMessages.addAll(messages);

        AiClient client = aiClientFactory.createClient(config);
        AiChatResponse response = client.chat(AiChatRequest.of(config.getModel(), requestMessages));
        if (!response.isSuccess()) {
            log.error("AI 对话失败: {}", response.getErrorMessage());
            return "AI 回复失败：" + response.getErrorMessage();
        }
        return response.getContent();
    }

    private String generateChartImage(List<Map<String, Object>> rows, String chartType, String chartTitle) {
        try {
            File chartFile = chartGenerationService.generateChart(rows, chartType, chartTitle);
            if (chartFile == null || !chartFile.exists()) {
                return null;
            }
            String dateFolder = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            String fileName = UUID.randomUUID().toString().substring(0, 8) + ".png";
            Path targetDir = Paths.get(uploadPath, "ai-charts", dateFolder);
            if (!Files.exists(targetDir)) {
                Files.createDirectories(targetDir);
            }
            Path targetFile = targetDir.resolve(fileName);
            Files.copy(chartFile.toPath(), targetFile, StandardCopyOption.REPLACE_EXISTING);
            if (!chartFile.delete()) {
                log.warn("临时图表文件删除失败: {}", chartFile.getAbsolutePath());
            }
            return "/storage/ai-charts/" + dateFolder + "/" + fileName;
        } catch (Exception e) {
            log.error("生成图表图片失败: type={}, title={}", chartType, chartTitle, e);
            return null;
        }
    }

    private String summarizeSqlResult(String userMessage, SqlGenerateResult sqlResult,
                                      List<Map<String, Object>> rows, String executeInfo,
                                      List<AiMessage> history) {
        AiConfig config = aiConfigService.getDefaultConfig();
        if (config == null) {
            return executeInfo + "\n\nSQL：" + sqlResult.getSql() + "\n\n结果：\n" + JSON.toJSONString(rows);
        }

        List<AiMessage> requestMessages = new ArrayList<>();
        requestMessages.add(AiMessage.system(SYSTEM_PROMPT_SQL_SUMMARY));
        if (history != null && !history.isEmpty()) {
            requestMessages.add(AiMessage.system("以下是对话历史，总结时请结合上下文理解用户意图。"));
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
