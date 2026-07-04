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
public class OpenAiCompatibleClient implements AiClient {

    private final String baseUrl;
    private final String apiKey;
    private final String defaultModel;
    private final Integer timeoutSeconds;
    private final RestTemplate restTemplate;

    public OpenAiCompatibleClient(String baseUrl, String apiKey, String defaultModel, Integer timeoutSeconds) {
        this.baseUrl = baseUrl != null ? baseUrl.replaceAll("/+$", "") : "https://api.openai.com/v1";
        this.apiKey = apiKey;
        this.defaultModel = defaultModel;
        this.timeoutSeconds = timeoutSeconds != null ? timeoutSeconds : 60;
        this.restTemplate = new RestTemplate();
        this.restTemplate.getMessageConverters().add(new org.springframework.http.converter.StringHttpMessageConverter());
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

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(body, headers),
                    String.class
            );
            return parseResponse(response.getBody());
        } catch (Exception e) {
            log.error("OpenAI compatible API call failed: {}", url, e);
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
            JSONObject choice = root.getJSONArray("choices").getJSONObject(0);
            JSONObject message = choice.getJSONObject("message");
            String content = message.getString("content");

            AiChatResponse response = new AiChatResponse();
            response.setContent(content);

            JSONObject usage = root.getJSONObject("usage");
            if (usage != null) {
                response.setPromptTokens(usage.getInteger("prompt_tokens"));
                response.setCompletionTokens(usage.getInteger("completion_tokens"));
                response.setTotalTokens(usage.getInteger("total_tokens"));
            }
            return response;
        } catch (Exception e) {
            log.error("Parse AI response failed: {}", json, e);
            return AiChatResponse.error("解析 AI 响应失败");
        }
    }
}
