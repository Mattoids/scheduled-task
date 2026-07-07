package com.mattoid.scheduled.task;

import com.mattoid.scheduled.entity.TaskWebCrawlConfig;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 网页预览上下文缓存，用于在 iframe srcdoc 与资源代理之间共享爬取配置。
 */
@Component
public class WebCrawlPreviewContextCache {

    private static final long TTL_MILLIS = 5 * 60 * 1000L;

    private final Map<String, PreviewContext> cache = new ConcurrentHashMap<>();

    public String put(TaskWebCrawlConfig config) {
        evictExpired();
        String token = UUID.randomUUID().toString();
        cache.put(token, new PreviewContext(config, Instant.now().toEpochMilli()));
        return token;
    }

    public PreviewContext get(String token) {
        if (token == null) {
            return null;
        }
        PreviewContext ctx = cache.get(token);
        if (ctx == null || isExpired(ctx)) {
            cache.remove(token);
            return null;
        }
        return ctx;
    }

    public void remove(String token) {
        cache.remove(token);
    }

    private void evictExpired() {
        long now = Instant.now().toEpochMilli();
        cache.entrySet().removeIf(e -> now - e.getValue().timestamp > TTL_MILLIS);
    }

    private boolean isExpired(PreviewContext ctx) {
        return Instant.now().toEpochMilli() - ctx.timestamp > TTL_MILLIS;
    }

    public record PreviewContext(TaskWebCrawlConfig config, long timestamp) {
    }
}
