package com.mattoid.scheduled.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiMessage {

    public static final String ROLE_SYSTEM = "system";
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";

    private String role;
    private String content;

    public static AiMessage system(String content) {
        return new AiMessage(ROLE_SYSTEM, content);
    }

    public static AiMessage user(String content) {
        return new AiMessage(ROLE_USER, content);
    }

    public static AiMessage assistant(String content) {
        return new AiMessage(ROLE_ASSISTANT, content);
    }
}
