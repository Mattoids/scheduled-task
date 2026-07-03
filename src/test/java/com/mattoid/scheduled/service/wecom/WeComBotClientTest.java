package com.mattoid.scheduled.service.wecom;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class WeComBotClientTest {

    private final WeComBotClient client = new WeComBotClient();

    @SuppressWarnings("unchecked")
    @Test
    void sendText_shouldPostCorrectBody() throws Exception {
        RestTemplate restTemplate = mock(RestTemplate.class);
        ResponseEntity<String> response = ResponseEntity.ok("{\"errcode\":0,\"errmsg\":\"ok\"}");
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response);

        client.restTemplate = restTemplate;
        client.objectMapper = new ObjectMapper();

        client.sendText("test-key", "hello");

        verify(restTemplate).postForEntity(eq("https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=test-key"),
                any(HttpEntity.class), eq(String.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    void sendMarkdown_shouldPostMarkdownMsgtype() throws Exception {
        RestTemplate restTemplate = mock(RestTemplate.class);
        ResponseEntity<String> response = ResponseEntity.ok("{\"errcode\":0,\"errmsg\":\"ok\"}");
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response);

        client.restTemplate = restTemplate;
        client.objectMapper = new ObjectMapper();

        client.sendMarkdown("test-key", "**bold**");

        verify(restTemplate).postForEntity(eq("https://qyapi.weixin.qq.com/cgi-bin/webhook/send?key=test-key"),
                any(HttpEntity.class), eq(String.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    void sendFile_shouldUploadMediaThenPostFileMessage() throws Exception {
        RestTemplate restTemplate = mock(RestTemplate.class);
        ResponseEntity<String> uploadResponse = ResponseEntity.ok(
                "{\"errcode\":0,\"errmsg\":\"ok\",\"type\":\"file\",\"media_id\":\"MEDIA_ID\"}");
        ResponseEntity<String> sendResponse = ResponseEntity.ok("{\"errcode\":0,\"errmsg\":\"ok\"}");
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(uploadResponse, sendResponse);

        client.restTemplate = restTemplate;
        client.objectMapper = new ObjectMapper();

        File file = File.createTempFile("report", ".txt");
        file.deleteOnExit();
        client.sendFile("test-key", file);

        verify(restTemplate, times(2)).postForEntity(anyString(), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void sendText_shouldDoNothingWhenContentBlank() throws Exception {
        RestTemplate restTemplate = mock(RestTemplate.class);
        client.restTemplate = restTemplate;

        client.sendText("test-key", "");
        client.sendText("test-key", null);

        verify(restTemplate, never()).postForEntity(anyString(), any(), any());
    }
}
