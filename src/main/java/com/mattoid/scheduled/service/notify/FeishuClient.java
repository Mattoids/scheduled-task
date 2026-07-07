package com.mattoid.scheduled.service.notify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class FeishuClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void sendText(String webhookUrl, String secret, String content) throws Exception {
        if (!StringUtils.hasText(content)) {
            return;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("msg_type", "text");
        Map<String, Object> text = new HashMap<>();
        text.put("text", content);
        body.put("content", text);
        post(webhookUrl, secret, body);
        log.info("飞书机器人文本消息发送成功");
    }

    public void sendMarkdown(String webhookUrl, String secret, String content) throws Exception {
        if (!StringUtils.hasText(content)) {
            return;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("msg_type", "interactive");
        Map<String, Object> card = new HashMap<>();
        card.put("config", Map.of("wide_screen_mode", true));
        card.put("elements", new Object[]{
                Map.of("tag", "div", "text", Map.of("tag", "lark_md", "content", content))
        });
        body.put("card", card);
        post(webhookUrl, secret, body);
        log.info("飞书机器人卡片消息发送成功");
    }

    private void post(String webhookUrl, String secret, Map<String, Object> body) throws Exception {
        String url = buildSignedUrl(webhookUrl, secret);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
        log.info("飞书机器人响应: status={}, body={}", response.getStatusCode(), response.getBody());
        JsonNode node = objectMapper.readTree(response.getBody());
        int code = node.path("code").asInt(-1);
        if (code != 0) {
            throw new RuntimeException("飞书机器人消息发送失败: " + response.getBody());
        }
    }

    private String buildSignedUrl(String webhookUrl, String secret) throws Exception {
        if (!StringUtils.hasText(secret)) {
            return webhookUrl;
        }
        long timestamp = System.currentTimeMillis() / 1000;
        String stringToSign = timestamp + "\n" + secret;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(stringToSign.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signData = mac.doFinal(new byte[0]);
        String sign = new String(Base64.getEncoder().encode(signData), StandardCharsets.UTF_8);
        return webhookUrl + "&timestamp=" + timestamp + "&sign=" + sign;
    }
}
