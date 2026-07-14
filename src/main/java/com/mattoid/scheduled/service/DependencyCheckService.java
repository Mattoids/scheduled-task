package com.mattoid.scheduled.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 系统依赖项检测服务。
 * <p>当前已知的运行期环境依赖为 Playwright Chromium 内核（尤其在 Linux 环境）。
 * 公网 IP 检测服务与企业微信 QR 登录接口默认可达，不纳入检测。
 * 若后续在不同操作系统上发现其它环境依赖，可在此按操作系统扩展检测逻辑。</p>
 */
@Slf4j
@Service
public class DependencyCheckService {

    /** 检测结果缓存 1 分钟，避免每次页面刷新都启动浏览器。 */
    private static final Duration CACHE_TTL = Duration.ofMinutes(1);

    private final BrowserCapabilityService browserCapabilityService;

    private final AtomicReference<CacheEntry> cache = new AtomicReference<>();

    public DependencyCheckService(BrowserCapabilityService browserCapabilityService) {
        this.browserCapabilityService = browserCapabilityService;
    }

    /**
     * 获取所有依赖项的检测结果（带缓存）。
     */
    public List<DependencyItem> checkDependencies() {
        CacheEntry entry = cache.get();
        Instant now = Instant.now();
        if (entry != null && Duration.between(entry.timestamp, now).compareTo(CACHE_TTL) < 0) {
            return entry.items;
        }

        List<DependencyItem> items = doCheck();
        cache.set(new CacheEntry(items, now));
        return items;
    }

    /**
     * 立即重新检测所有依赖项。
     */
    public List<DependencyItem> refreshDependencies() {
        List<DependencyItem> items = doCheck();
        cache.set(new CacheEntry(items, Instant.now()));
        return items;
    }

    private List<DependencyItem> doCheck() {
        boolean chromium = browserCapabilityService.isChromiumAvailable();
        log.info("[DependencyCheck] Chromium 内核检测结果: {}", chromium ? "可用" : "不可用");
        return List.of(
                new DependencyItem("chromium", "Chromium 内核", chromium,
                        chromium ? "Chromium 内核已安装"
                                : "Chromium 内核未安装，扫码登录与 IP 同步不可用")
        );
    }

    /**
     * 单个依赖项检测结果。
     */
    public record DependencyItem(String key, String name, boolean available, String message) {
    }

    private record CacheEntry(List<DependencyItem> items, Instant timestamp) {
    }
}
