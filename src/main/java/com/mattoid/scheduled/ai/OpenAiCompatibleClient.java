package com.mattoid.scheduled.ai;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.mattoid.scheduled.util.JsonResponseParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class OpenAiCompatibleClient implements AiClient {

    /** 瞬态错误（5xx/429/超时）的最大尝试次数：首次 + 1 次重试 */
    private static final int MAX_ATTEMPTS = 2;
    private static final long RETRY_BACKOFF_MS = 1500L;

    private final String baseUrl;
    private final String apiKey;
    private final String defaultModel;
    private final RestTemplate restTemplate;

    public OpenAiCompatibleClient(String baseUrl, String apiKey, String defaultModel, RestTemplate restTemplate) {
        this.baseUrl = baseUrl != null ? baseUrl.replaceAll("/+$", "") : "https://api.openai.com/v1";
        this.apiKey = apiKey;
        this.defaultModel = defaultModel;
        this.restTemplate = restTemplate;
    }

    @Override
    public AiChatResponse chat(AiChatRequest request) {
        String url = baseUrl + "/chat/completions";

        Map<String, Object> body = new HashMap<>();
        body.put("model", request.getModel() != null ? request.getModel() : defaultModel);
        body.put("messages", request.getMessages().stream()
                .map(m -> Map.of("role", m.getRole(), "content", m.getContent()))
                .collect(Collectors.toList()));
        if (request.getTemperature() != null) {
            body.put("temperature", request.getTemperature());
        }
        if (request.getMaxTokens() != null) {
            body.put("max_tokens", request.getMaxTokens());
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.isEmpty()) {
            headers.setBearerAuth(apiKey);
        }

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
                return parseResponse(response.getBody());
            } catch (HttpStatusCodeException e) {
                int status = e.getStatusCode().value();
                boolean retryable = status == 429 || status >= 500;
                log.warn("AI 上游返回 HTTP {}（第 {}/{} 次）{}", status, attempt, MAX_ATTEMPTS,
                        retryable && attempt < MAX_ATTEMPTS ? "，将重试" : "");
                if (retryable && attempt < MAX_ATTEMPTS) {
                    sleep(RETRY_BACKOFF_MS);
                    continue;
                }
                return AiChatResponse.error(buildHttpErrorMessage(status));
            } catch (ResourceAccessException e) {
                // 连接/读取超时等网络异常
                log.warn("AI 请求网络异常（第 {}/{} 次）：{}", attempt, MAX_ATTEMPTS, e.getMessage());
                if (attempt < MAX_ATTEMPTS) {
                    sleep(RETRY_BACKOFF_MS);
                    continue;
                }
                return AiChatResponse.error("AI 请求超时或网络异常，请稍后重试");
            } catch (Exception e) {
                log.error("OpenAI compatible API call failed: {}", url, e);
                return AiChatResponse.error("AI 调用失败: " + e.getMessage());
            }
        }
        return AiChatResponse.error("AI 调用失败");
    }

    private String buildHttpErrorMessage(int status) {
        return switch (status) {
            case 401, 403 -> "AI 鉴权失败，请检查 API Key";
            case 429 -> "AI 请求过于频繁，请稍后重试";
            case 500, 502, 503, 504 -> "AI 服务暂时不可用（上游 HTTP " + status + "），请稍后重试";
            default -> "AI 调用失败（HTTP " + status + "）";
        };
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private AiChatResponse parseResponse(String json) {
        JSONObject root = JsonResponseParser.parseObject(json);
        if (root == null) {
            log.warn("AI 响应为空或非 JSON，原始响应: {}", JsonResponseParser.truncate(json));
            return AiChatResponse.error("AI 响应解析失败");
        }
        if (root.containsKey("error")) {
            String errorMessage = JsonResponseParser.pathString(root, "error.message");
            return AiChatResponse.error(StringUtils.hasText(errorMessage) ? errorMessage : "AI 返回错误");
        }
        JSONArray choices = JsonResponseParser.pathArray(root, "choices");
        if (choices == null || choices.isEmpty()) {
            log.warn("AI 响应缺少 choices，原始响应: {}", JsonResponseParser.truncate(json));
            return AiChatResponse.error("AI 未返回有效结果");
        }
        JSONObject message = choices.getJSONObject(0).getJSONObject("message");
        String content = message != null ? message.getString("content") : null;
        if (!StringUtils.hasText(content)) {
            // 便于排查兼容厂商（如 SenseNova）返回结构差异或 content 为空的情况
            log.warn("AI 响应 message.content 为空，原始响应: {}", JsonResponseParser.truncate(json));
        }

        AiChatResponse response = new AiChatResponse();
        response.setContent(content);

        JSONObject usage = root.getJSONObject("usage");
        if (usage != null) {
            response.setPromptTokens(usage.getInteger("prompt_tokens"));
            response.setCompletionTokens(usage.getInteger("completion_tokens"));
            response.setTotalTokens(usage.getInteger("total_tokens"));
        }
        return response;
    }
}
