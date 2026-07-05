package com.mattoid.scheduled.service.wecom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class WeComBotClient {

    private static final String SEND_URL = "https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=%s";
    private static final String UPLOAD_URL = "https://qyapi.weixin.qq.com/cgi-bin/webhook/upload_media?key=%s&type=file";

    RestTemplate restTemplate = new RestTemplate();
    ObjectMapper objectMapper = new ObjectMapper();

    public void sendText(String webhookKey, String content) throws Exception {
        sendText(webhookKey, content, null);
    }

    public void sendText(String webhookKey, String content, List<String> mentionedList) throws Exception {
        if (!StringUtils.hasText(content)) {
            return;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("msgtype", "text");
        Map<String, Object> text = new HashMap<>();
        text.put("content", content);
        if (mentionedList != null && !mentionedList.isEmpty()) {
            text.put("mentioned_list", mentionedList);
        }
        body.put("text", text);
        post(webhookKey, body);
        log.info("企业微信群机器人文本消息发送成功: key={}", webhookKey);
    }

    public void sendMarkdown(String webhookKey, String content) throws Exception {
        sendMarkdown(webhookKey, content, null);
    }

    public void sendMarkdown(String webhookKey, String content, List<String> mentionedList) throws Exception {
        if (!StringUtils.hasText(content)) {
            return;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("msgtype", "markdown");
        Map<String, Object> markdown = new HashMap<>();
        markdown.put("content", content);
        if (mentionedList != null && !mentionedList.isEmpty()) {
            markdown.put("mentioned_list", mentionedList);
        }
        body.put("markdown", markdown);
        post(webhookKey, body);
        log.info("企业微信群机器人 Markdown 消息发送成功: key={}", webhookKey);
    }

    public void sendFile(String webhookKey, File file) throws Exception {
        if (file == null || !file.exists()) {
            throw new IllegalArgumentException("文件不存在: " + (file == null ? "null" : file.getAbsolutePath()));
        }
        String mediaId = uploadMedia(webhookKey, file);
        Map<String, Object> body = new HashMap<>();
        body.put("msgtype", "file");
        Map<String, String> fileMsg = new HashMap<>();
        fileMsg.put("media_id", mediaId);
        body.put("file", fileMsg);
        post(webhookKey, body);
        log.info("企业微信群机器人文件消息发送成功: key={}, file={}", webhookKey, file.getName());
    }

    private String uploadMedia(String webhookKey, File file) throws Exception {
        String url = String.format(UPLOAD_URL, webhookKey);
        String maskedUrl = maskUrl(url);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("media", new FileSystemResource(file));

        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
        log.info("企业微信群机器人上传文件: url={}, fileName={}, fileSize={}",
                maskedUrl, file.getName(), file.length());
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
        log.info("企业微信群机器人文件上传响应: url={}, status={}, response={}",
                maskedUrl, response.getStatusCode(), response.getBody());
        JsonNode node = objectMapper.readTree(response.getBody());
        int errcode = node.path("errcode").asInt(-1);
        if (errcode != 0) {
            throw new RuntimeException("群机器人文件上传失败: " + response.getBody());
        }
        return node.path("media_id").asText();
    }

    private void post(String webhookKey, Map<String, Object> body) throws Exception {
        String url = String.format(SEND_URL, webhookKey);
        String maskedUrl = maskUrl(url);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        String bodyJson = objectMapper.writeValueAsString(body);
        log.info("企业微信群机器人发送消息: url={}, body={}", maskedUrl, bodyJson);
        ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
        log.info("企业微信群机器人消息响应: url={}, status={}, response={}",
                maskedUrl, response.getStatusCode(), response.getBody());
        JsonNode node = objectMapper.readTree(response.getBody());
        int errcode = node.path("errcode").asInt(-1);
        if (errcode != 0) {
            throw new RuntimeException("群机器人消息发送失败: " + response.getBody());
        }
    }

    private String maskUrl(String url) {
        if (url == null) {
            return null;
        }
        return url.replaceAll("key=[^&]+", "key=***");
    }
}
