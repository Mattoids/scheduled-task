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
 * <p>为避免在依赖检测阶段触发 Playwright 驱动或浏览器下载，
 * 默认仅通过文件系统定位本地缓存；真正的启动检测仅作为显式刷新入口保留。</p>
 */
@Slf4j
@Service
public class BrowserCapabilityService {

    /** 检测结果缓存 5 分钟，避免每次页面刷新都启动浏览器。 */
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final int LAUNCH_TIMEOUT_MS = 10_000;

    private final AtomicReference<CacheEntry> cache = new AtomicReference<>();
    private final PlaywrightBrowserLocator browserLocator;

    public BrowserCapabilityService(PlaywrightBrowserLocator browserLocator) {
        this.browserLocator = browserLocator;
    }

    /**
     * 判断 Chromium 内核是否可用（带缓存）。
     * <p>仅做文件系统定位，不创建 Playwright 实例，因此不会触发任何下载。</p>
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
     * <p>此接口会实际尝试启动 Chromium，用于用户显式刷新；
     * 普通依赖检测请使用 {@link #isChromiumAvailable()} 以避免触发下载。</p>
     */
    public boolean refreshChromiumStatus() {
        boolean available = checkChromiumLaunchable();
        cache.set(new CacheEntry(available, Instant.now()));
        return available;
    }

    /**
     * 通过文件系统定位 Chromium 可执行文件，不创建 Playwright 实例。
     */
    private boolean checkChromium() {
        boolean present = browserLocator.isChromiumPresent();
        if (present) {
            log.info("[ChromiumCheck] 已定位到本地 Chromium 可执行文件");
        } else {
            log.warn("[ChromiumCheck] 未在本地 Playwright 缓存中找到 Chromium 可执行文件");
        }
        return present;
    }

    /**
     * 实际尝试启动 Chromium，验证其是否可运行。
     */
    private boolean checkChromiumLaunchable() {
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
