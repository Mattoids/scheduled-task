package com.mattoid.scheduled.ai;

import java.util.List;

public interface AiClient {

    AiChatResponse chat(AiChatRequest request);

    default AiChatResponse chat(String userMessage) {
        return chat(AiChatRequest.of(null, List.of(AiMessage.user(userMessage))));
    }

    default AiChatResponse chat(List<AiMessage> messages) {
        return chat(AiChatRequest.of(null, messages));
    }
}
