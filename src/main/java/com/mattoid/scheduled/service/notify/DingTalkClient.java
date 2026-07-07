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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class DingTalkClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void sendText(String webhookUrl, String secret, String content, List<String> atMobiles, Boolean atAll) throws Exception {
        if (!StringUtils.hasText(content)) {
            return;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("msgtype", "text");
        Map<String, Object> text = new HashMap<>();
        text.put("content", content);
        body.put("text", text);
        body.put("at", buildAt(atMobiles, atAll));
        post(webhookUrl, secret, body);
        log.info("钉钉机器人文本消息发送成功");
    }

    public void sendMarkdown(String webhookUrl, String secret, String title, String content, List<String> atMobiles, Boolean atAll) throws Exception {
        if (!StringUtils.hasText(content)) {
            return;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("msgtype", "markdown");
        Map<String, Object> markdown = new HashMap<>();
        markdown.put("title", StringUtils.hasText(title) ? title : "通知");
        markdown.put("text", content);
        body.put("markdown", markdown);
        body.put("at", buildAt(atMobiles, atAll));
        post(webhookUrl, secret, body);
        log.info("钉钉机器人 Markdown 消息发送成功");
    }

    private Map<String, Object> buildAt(List<String> atMobiles, Boolean atAll) {
        Map<String, Object> at = new HashMap<>();
        if (atMobiles != null && !atMobiles.isEmpty()) {
            at.put("atMobiles", atMobiles);
        }
        if (atAll != null && atAll) {
            at.put("isAtAll", true);
        }
        return at;
    }

    private void post(String webhookUrl, String secret, Map<String, Object> body) throws Exception {
        String url = buildSignedUrl(webhookUrl, secret);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
        log.info("钉钉机器人响应: status={}, body={}", response.getStatusCode(), response.getBody());
        JsonNode node = objectMapper.readTree(response.getBody());
        int errcode = node.path("errcode").asInt(-1);
        if (errcode != 0) {
            throw new RuntimeException("钉钉机器人消息发送失败: " + response.getBody());
        }
    }

    private String buildSignedUrl(String webhookUrl, String secret) throws Exception {
        if (!StringUtils.hasText(secret)) {
            return webhookUrl;
        }
        long timestamp = System.currentTimeMillis();
        String stringToSign = timestamp + "\n" + secret;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
        String sign = URLEncoder.encode(Base64.getEncoder().encodeToString(signData), StandardCharsets.UTF_8);
        return webhookUrl + "&timestamp=" + timestamp + "&sign=" + sign;
    }
}
