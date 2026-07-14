package com.mattoid.scheduled.service;

import com.mattoid.scheduled.service.wecom.WeComIpSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 系统依赖项检测服务。
 * <p>企业微信扫码登录、可信 IP 自动同步等功能依赖多项外部/系统资源，
 * 本服务统一检测并缓存结果，供前端展示与功能开关控制。</p>
 */
@Slf4j
@Service
public class DependencyCheckService {

    /** 检测结果缓存 1 分钟，避免每次页面刷新都进行网络探测。 */
    private static final Duration CACHE_TTL = Duration.ofMinutes(1);
    private static final int CHECK_TIMEOUT_SECONDS = 15;

    private final BrowserCapabilityService browserCapabilityService;
    private final WeComIpSyncService weComIpSyncService;

    private final AtomicReference<CacheEntry> cache = new AtomicReference<>();

    public DependencyCheckService(BrowserCapabilityService browserCapabilityService,
                                  WeComIpSyncService weComIpSyncService) {
        this.browserCapabilityService = browserCapabilityService;
        this.weComIpSyncService = weComIpSyncService;
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
        Instant start = Instant.now();

        CompletableFuture<Boolean> chromiumFuture = supplyAsyncWithTimeout(
                () -> browserCapabilityService.isChromiumAvailable(), "Chromium 内核检测异常");
        CompletableFuture<Boolean> ipSourceFuture = supplyAsyncWithTimeout(
                () -> weComIpSyncService.isAnyIpSourceReachable(), "IP 检测源探测异常");
        CompletableFuture<Boolean> wecomApiFuture = supplyAsyncWithTimeout(
                () -> weComIpSyncService.isWeComQrApiReachable(), "企业微信 QR 接口探测异常");

        boolean chromium = chromiumFuture.join();
        boolean ipSource = ipSourceFuture.join();
        boolean wecomApi = wecomApiFuture.join();

        log.info("[DependencyCheck] 依赖检测完成，耗时 {}ms: chromium={}, ipSource={}, wecomApi={}",
                Duration.between(start, Instant.now()).toMillis(), chromium, ipSource, wecomApi);

        return List.of(
                new DependencyItem("chromium", "Chromium 内核", chromium,
                        chromium ? "Chromium 内核已安装"
                                : "Chromium 内核未安装，扫码登录与 IP 同步不可用"),
                new DependencyItem("ipDetection", "公网 IP 检测服务", ipSource,
                        ipSource ? "公网 IP 检测服务可访问"
                                : "无法访问任何公网 IP 检测服务，自动同步 IP 不可用"),
                new DependencyItem("wecomApi", "企业微信 QR 登录接口", wecomApi,
                        wecomApi ? "企业微信 QR 登录接口可访问"
                                : "无法访问企业微信 QR 登录接口，扫码登录与 IP 同步不可用")
        );
    }

    private CompletableFuture<Boolean> supplyAsyncWithTimeout(java.util.function.Supplier<Boolean> supplier, String errorLabel) {
        return CompletableFuture.supplyAsync(supplier)
                .orTimeout(CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .exceptionally(ex -> {
                    log.warn("[DependencyCheck] {}: {}", errorLabel, ex.getMessage());
                    return false;
                });
    }

    /**
     * 单个依赖项检测结果。
     */
    public record DependencyItem(String key, String name, boolean available, String message) {
    }

    private record CacheEntry(List<DependencyItem> items, Instant timestamp) {
    }
}
