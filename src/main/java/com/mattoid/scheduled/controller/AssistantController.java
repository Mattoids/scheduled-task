package com.mattoid.scheduled.controller;

import com.mattoid.scheduled.common.Result;
import com.mattoid.scheduled.dto.IntentResult;
import com.mattoid.scheduled.entity.AiConversation;
import com.mattoid.scheduled.service.AiAssistantService;
import com.mattoid.scheduled.service.AiConversationService;
import lombok.Data;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private final AiAssistantService aiAssistantService;
    private final AiConversationService aiConversationService;

    public AssistantController(AiAssistantService aiAssistantService,
                               AiConversationService aiConversationService) {
        this.aiAssistantService = aiAssistantService;
        this.aiConversationService = aiConversationService;
    }

    @PreAuthorize("hasAuthority('task:view')")
    @PostMapping("/parse-intent")
    public Result<IntentResult> parseIntent(@RequestBody ParseIntentRequest request) {
        if (request == null || !hasText(request.getContent())) {
            return Result.error("内容不能为空");
        }
        return Result.ok(aiAssistantService.parseIntent(request.getContent()));
    }

    @PreAuthorize("hasAuthority('task:view')")
    @PostMapping("/optimize-notification")
    public Result<OptimizeNotificationResponse> optimizeNotification(@RequestBody OptimizeNotificationRequest request) {
        if (request == null) {
            return Result.error("请求不能为空");
        }
        AiAssistantService.NotificationContent content = aiAssistantService.optimizeNotification(
                request.getSubject(), request.getBody(), request.getContext());
        OptimizeNotificationResponse response = new OptimizeNotificationResponse();
        response.setSubject(content.subject());
        response.setBody(content.body());
        return Result.ok(response);
    }

    @PreAuthorize("hasAuthority('task:view')")
    @PostMapping("/chat")
    public Result<AiConversation> chat(@RequestBody ChatRequest request) {
        if (request == null || !hasText(request.getMessage())) {
            return Result.error("消息不能为空");
        }
        return Result.ok(aiConversationService.chat(request.getSessionId(), request.getDatasourceId(), request.getMessage()));
    }

    @PreAuthorize("hasAuthority('task:view')")
    @PostMapping("/generate-config")
    public Result<AiAssistantService.NaturalConfigResult> generateConfig(@RequestBody GenerateConfigRequest request) {
        if (request == null || !hasText(request.getContent())) {
            return Result.error("内容不能为空");
        }
        return Result.ok(aiAssistantService.generateConfig(request.getContent()));
    }

    private boolean hasText(String str) {
        return str != null && !str.trim().isEmpty();
    }

    @Data
    public static class ParseIntentRequest {
        private String content;
    }

    @Data
    public static class OptimizeNotificationRequest {
        private String subject;
        private String body;
        private String context;
    }

    @Data
    public static class OptimizeNotificationResponse {
        private String subject;
        private String body;
    }

    @Data
    public static class ChatRequest {
        private String sessionId;
        private Long datasourceId;
        private String message;
    }

    @Data
    public static class GenerateConfigRequest {
        private String content;
    }
}
