package com.mattoid.scheduled.config;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * 共享 RestTemplate 配置。
 * <p>
 * 使用 Apache HttpClient 5 连接池，统一超时、重试与错误处理配置，
 * 避免各 AI 客户端与通知客户端各自创建无连接池的 RestTemplate 实例。
 */
@Configuration
public class RestTemplateConfig {

    private static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 10;
    private static final int DEFAULT_READ_TIMEOUT_SECONDS = 120;
    private static final int DEFAULT_CONNECTION_REQUEST_TIMEOUT_SECONDS = 10;
    private static final int MAX_TOTAL_CONNECTIONS = 200;
    private static final int MAX_PER_ROUTE_CONNECTIONS = 50;

    @Bean
    public PoolingHttpClientConnectionManager httpClientConnectionManager() {
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(MAX_TOTAL_CONNECTIONS);
        connectionManager.setDefaultMaxPerRoute(MAX_PER_ROUTE_CONNECTIONS);
        return connectionManager;
    }

    @Bean
    public CloseableHttpClient sharedHttpClient(PoolingHttpClientConnectionManager connectionManager) {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(DEFAULT_CONNECT_TIMEOUT_SECONDS))
                .setResponseTimeout(Timeout.ofSeconds(DEFAULT_READ_TIMEOUT_SECONDS))
                .setConnectionRequestTimeout(Timeout.ofSeconds(DEFAULT_CONNECTION_REQUEST_TIMEOUT_SECONDS))
                .build();

        return HttpClientBuilder.create()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .build();
    }

    @Bean
    public RestTemplate sharedRestTemplate(RestTemplateBuilder builder, CloseableHttpClient httpClient) {
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
        return builder
                .requestFactory(() -> requestFactory)
                .build();
    }
}
