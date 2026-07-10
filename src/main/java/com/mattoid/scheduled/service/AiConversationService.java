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
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
            1. 直接回答用户问题，不要重复 SQL。
            2. 如果结果是数据表格，请用文字概括关键信息；必要时可列出少量具体数据。
            3. 如果执行失败或没有数据，请说明原因并给出建议。
            """;

    private final AiConversationMapper aiConversationMapper;
    private final AiKnowledgeDocService aiKnowledgeDocService;
    private final AiAssistantService aiAssistantService;
    private final AiConfigService aiConfigService;
    private final AiClientFactory aiClientFactory;
    private final SqlExecutor sqlExecutor;

    public AiConversationService(AiConversationMapper aiConversationMapper,
                                 AiKnowledgeDocService aiKnowledgeDocService,
                                 AiAssistantService aiAssistantService,
                                 AiConfigService aiConfigService,
                                 AiClientFactory aiClientFactory,
                                 SqlExecutor sqlExecutor) {
        this.aiConversationMapper = aiConversationMapper;
        this.aiKnowledgeDocService = aiKnowledgeDocService;
        this.aiAssistantService = aiAssistantService;
        this.aiConfigService = aiConfigService;
        this.aiClientFactory = aiClientFactory;
        this.sqlExecutor = sqlExecutor;
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
        SqlGenerateResult sqlResult = aiAssistantService.generateSql(schemaDoc, userMessage);
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

        return summarizeSqlResult(userMessage, sqlResult, rows, executeInfo);
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

    private String summarizeSqlResult(String userMessage, SqlGenerateResult sqlResult,
                                      List<Map<String, Object>> rows, String executeInfo) {
        AiConfig config = aiConfigService.getDefaultConfig();
        if (config == null) {
            return executeInfo + "\n\nSQL：" + sqlResult.getSql() + "\n\n结果：\n" + JSON.toJSONString(rows);
        }

        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("用户问题：").append(userMessage).append("\n\n");
        userPrompt.append("生成的 SQL：").append(sqlResult.getSql()).append("\n\n");
        userPrompt.append("执行信息：").append(executeInfo).append("\n\n");
        userPrompt.append("执行结果（JSON）：\n").append(JSON.toJSONString(rows)).append("\n\n");
        userPrompt.append("请根据以上信息回复用户。");

        List<AiMessage> requestMessages = new ArrayList<>();
        requestMessages.add(AiMessage.system(SYSTEM_PROMPT_SQL_SUMMARY));
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
