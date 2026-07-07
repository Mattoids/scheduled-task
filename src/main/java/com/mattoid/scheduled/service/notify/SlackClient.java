package com.mattoid.scheduled.service.notify;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class SlackClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void sendText(String webhookUrl, String content, String channel, String username) throws Exception {
        if (!StringUtils.hasText(content)) {
            return;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("text", content);
        if (StringUtils.hasText(channel)) {
            body.put("channel", channel);
        }
        if (StringUtils.hasText(username)) {
            body.put("username", username);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(webhookUrl, request, String.class);
        log.info("Slack 响应: status={}, body={}", response.getStatusCode(), response.getBody());
        String responseBody = response.getBody();
        if (responseBody != null && !responseBody.trim().equals("ok")) {
            throw new RuntimeException("Slack 消息发送失败: " + responseBody);
        }
        log.info("Slack 消息发送成功");
    }
}
