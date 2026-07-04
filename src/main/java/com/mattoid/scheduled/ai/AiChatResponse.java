package com.mattoid.scheduled.ai;

import lombok.Data;

@Data
public class AiChatResponse {

    private String content;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private String errorMessage;

    public static AiChatResponse error(String message) {
        AiChatResponse response = new AiChatResponse();
        response.setErrorMessage(message);
        return response;
    }

    public boolean isSuccess() {
        return errorMessage == null;
    }
}
