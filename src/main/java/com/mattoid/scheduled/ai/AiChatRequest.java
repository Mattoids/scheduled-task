package com.mattoid.scheduled.ai;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AiChatRequest {

    private String model;
    private List<AiMessage> messages = new ArrayList<>();
    private Double temperature = 0.7;
    private Integer maxTokens = 2048;

    public static AiChatRequest of(String model, List<AiMessage> messages) {
        AiChatRequest request = new AiChatRequest();
        request.setModel(model);
        request.setMessages(messages);
        return request;
    }
}
