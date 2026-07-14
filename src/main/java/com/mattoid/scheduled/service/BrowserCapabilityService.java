package com.mattoid.scheduled.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 检测系统是否具备 Playwright Chromium 运行能力。
 * <p>企业微信可信 IP 同步、扫码登录等流程依赖 Chromium 内核，
 * 本服务用于前端按需隐藏相关功能并在首页展示环境状态。</p>
 */
@Slf4j
@Service
public class BrowserCapabilityService {

    /** 检测结果缓存 5 分钟，避免每次页面刷新都启动浏览器。 */
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final int LAUNCH_TIMEOUT_MS = 10_000;

    private final AtomicReference<CacheEntry> cache = new AtomicReference<>();

    /**
     * 判断 Chromium 内核是否可用（带缓存）。
     */
    public boolean isChromiumAvailable() {
        CacheEntry entry = cache.get();
        Instant now = Instant.now();
        if (entry != null && Duration.between(entry.timestamp, now).compareTo(CACHE_TTL) < 0) {
            return entry.available;
        }

        boolean available = checkChromium();
        cache.set(new CacheEntry(available, now));
        return available;
    }

    /**
     * 立即重新检测 Chromium 可用性，并刷新缓存。
     */
    public boolean refreshChromiumStatus() {
        boolean available = checkChromium();
        cache.set(new CacheEntry(available, Instant.now()));
        return available;
    }

    private boolean checkChromium() {
        try (Playwright playwright = Playwright.create()) {
            BrowserType chromium = playwright.chromium();
            Browser browser = chromium.launch(new BrowserType.LaunchOptions()
                    .setHeadless(true)
                    .setTimeout(LAUNCH_TIMEOUT_MS));
            browser.close();
            log.info("[ChromiumCheck] Chromium 内核检测通过");
            return true;
        } catch (Exception e) {
            log.warn("[ChromiumCheck] Chromium 内核不可用: {}", e.getMessage());
            return false;
        }
    }

    private record CacheEntry(boolean available, Instant timestamp) {
    }
}
