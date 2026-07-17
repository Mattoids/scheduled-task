package com.mattoid.scheduled.ai;

import com.mattoid.scheduled.entity.AiConfig;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

@Component
public class AiClientFactory {

    public static final String PROVIDER_OPENAI = "OPENAI";
    public static final String PROVIDER_ANTHROPIC = "ANTHROPIC";
    public static final String PROVIDER_AZURE_OPENAI = "AZURE_OPENAI";
    public static final String PROVIDER_OLLAMA = "OLLAMA";
    public static final String PROVIDER_CUSTOM = "CUSTOM";

    private final RestTemplate restTemplate;

    public AiClientFactory(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public AiClient createClient(AiConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("AI 配置不能为空");
        }
        String provider = config.getProvider() != null ? config.getProvider().toUpperCase() : "";
        String baseUrl = resolveBaseUrl(config);
        String apiKey = config.getApiKey();
        String model = config.getModel();
        Integer timeout = config.getTimeoutSeconds();

        switch (provider) {
            case PROVIDER_OPENAI:
                return new OpenAiCompatibleClient(baseUrl, apiKey, model, restTemplate);
            case PROVIDER_AZURE_OPENAI:
                return new OpenAiCompatibleClient(baseUrl, apiKey, model, restTemplate);
            case PROVIDER_OLLAMA:
                return new OpenAiCompatibleClient(baseUrl, null, model, restTemplate);
            case PROVIDER_CUSTOM:
                return new OpenAiCompatibleClient(baseUrl, apiKey, model, restTemplate);
            case PROVIDER_ANTHROPIC:
                return new AnthropicClient(baseUrl, apiKey, model, timeout);
            default:
                throw new IllegalArgumentException("不支持的 AI 厂商: " + config.getProvider());
        }
    }

    private String resolveBaseUrl(AiConfig config) {
        if (StringUtils.hasText(config.getBaseUrl())) {
            return config.getBaseUrl();
        }
        return switch (config.getProvider().toUpperCase()) {
            case PROVIDER_OPENAI -> "https://api.openai.com/v1";
            case PROVIDER_ANTHROPIC -> "https://api.anthropic.com/v1";
            case PROVIDER_AZURE_OPENAI -> "";
            case PROVIDER_OLLAMA -> "http://localhost:11434/v1";
            default -> "";
        };
    }
}
