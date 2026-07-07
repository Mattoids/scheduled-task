package com.mattoid.scheduled.service.notify;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class WebhookClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void send(String url, String method, Map<String, String> headers, String bodyTemplate,
                     Map<String, Object> placeholders, Integer timeoutSeconds) throws Exception {
        if (!StringUtils.hasText(url)) {
            throw new IllegalArgumentException("Webhook URL 不能为空");
        }
        String requestMethod = StringUtils.hasText(method) ? method.toUpperCase() : "POST";
        String body = buildBody(bodyTemplate, placeholders);

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        if (headers != null) {
            headers.forEach(httpHeaders::add);
        }
        HttpEntity<String> request = new HttpEntity<>(body, httpHeaders);

        ResponseEntity<String> response;
        if ("GET".equals(requestMethod)) {
            response = restTemplate.exchange(url, HttpMethod.GET, request, String.class);
        } else if ("PUT".equals(requestMethod)) {
            response = restTemplate.exchange(url, HttpMethod.PUT, request, String.class);
        } else {
            response = restTemplate.exchange(url, HttpMethod.POST, request, String.class);
        }
        log.info("Webhook 响应: status={}, body={}", response.getStatusCode(), response.getBody());
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Webhook 调用失败: " + response.getStatusCode());
        }
    }

    private String buildBody(String bodyTemplate, Map<String, Object> placeholders) {
        if (!StringUtils.hasText(bodyTemplate)) {
            if (placeholders == null) {
                return null;
            }
            try {
                return objectMapper.writeValueAsString(placeholders);
            } catch (Exception e) {
                log.warn("序列化 Webhook 占位符失败", e);
                return null;
            }
        }
        String result = bodyTemplate;
        if (placeholders != null) {
            for (Map.Entry<String, Object> entry : placeholders.entrySet()) {
                result = result.replace("${" + entry.getKey() + "}", String.valueOf(entry.getValue()));
            }
        }
        return result;
    }

    public Map<String, Object> buildPlaceholders(String title, String content) {
        Map<String, Object> map = new HashMap<>();
        map.put("title", StringUtils.hasText(title) ? title : "");
        map.put("content", StringUtils.hasText(content) ? content : "");
        return map;
    }
}
