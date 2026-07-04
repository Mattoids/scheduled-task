package com.mattoid.scheduled.ai;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
public class AnthropicClient implements AiClient {

    private final String baseUrl;
    private final String apiKey;
    private final String defaultModel;
    private final RestTemplate restTemplate;

    public AnthropicClient(String baseUrl, String apiKey, String defaultModel, Integer timeoutSeconds) {
        this.baseUrl = baseUrl != null ? baseUrl.replaceAll("/+$", "") : "https://api.anthropic.com/v1";
        this.apiKey = apiKey;
        this.defaultModel = defaultModel;
        this.restTemplate = new RestTemplate();
    }

    @Override
    public AiChatResponse chat(AiChatRequest request) {
        String url = baseUrl + "/messages";

        List<Map<String, String>> messages = request.getMessages().stream()
                .filter(m -> !AiMessage.ROLE_SYSTEM.equals(m.getRole()))
                .map(m -> Map.of("role", m.getRole(), "content", m.getContent()))
                .collect(Collectors.toList());

        String systemPrompt = request.getMessages().stream()
                .filter(m -> AiMessage.ROLE_SYSTEM.equals(m.getRole()))
                .map(AiMessage::getContent)
                .collect(Collectors.joining("\n"));

        Map<String, Object> body = new HashMap<>();
        body.put("model", request.getModel() != null ? request.getModel() : defaultModel);
        body.put("messages", messages);
        body.put("max_tokens", request.getMaxTokens() != null ? request.getMaxTokens() : 2048);
        if (request.getTemperature() != null) {
            body.put("temperature", request.getTemperature());
        }
        if (!systemPrompt.isEmpty()) {
            body.put("system", systemPrompt);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", apiKey);
        headers.set("anthropic-version", "2023-06-01");

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class
            );
            return parseResponse(response.getBody());
        } catch (Exception e) {
            log.error("Anthropic API call failed: {}", url, e);
            return AiChatResponse.error("AI 调用失败: " + e.getMessage());
        }
    }

    private AiChatResponse parseResponse(String json) {
        try {
            JSONObject root = JSON.parseObject(json);
            if (root.containsKey("error")) {
                JSONObject error = root.getJSONObject("error");
                return AiChatResponse.error(error.getString("message"));
            }
            String content = root.getJSONArray("content").getJSONObject(0).getString("text");

            AiChatResponse response = new AiChatResponse();
            response.setContent(content);
            response.setPromptTokens(root.getJSONObject("usage").getInteger("input_tokens"));
            response.setCompletionTokens(root.getJSONObject("usage").getInteger("output_tokens"));
            return response;
        } catch (Exception e) {
            log.error("Parse Anthropic response failed: {}", json, e);
            return AiChatResponse.error("解析 AI 响应失败");
        }
    }
}
