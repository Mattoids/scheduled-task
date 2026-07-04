package com.mattoid.scheduled.controller;

import com.mattoid.scheduled.common.Result;
import com.mattoid.scheduled.dto.IntentResult;
import com.mattoid.scheduled.service.AiAssistantService;
import lombok.Data;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/assistant")
public class AssistantController {

    private final AiAssistantService aiAssistantService;

    public AssistantController(AiAssistantService aiAssistantService) {
        this.aiAssistantService = aiAssistantService;
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
}
