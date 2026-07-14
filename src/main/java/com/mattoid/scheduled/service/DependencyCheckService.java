package com.mattoid.scheduled.service;

import com.microsoft.playwright.Playwright;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 系统依赖项检测服务。
 * <p>当前已知的运行期环境依赖为 Playwright Chromium 内核（尤其在 Linux 环境）。
 * 不同操作系统会列出对应的环境依赖，并根据实际检测结果展示状态。
 * 公网 IP 检测服务与企业微信 QR 登录接口默认可达，不纳入检测。</p>
 */
@Slf4j
@Service
public class DependencyCheckService {

    /** 检测结果缓存 1 分钟，避免每次页面刷新都启动浏览器或执行 ldd。 */
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
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (os.contains("linux")) {
            return checkLinuxDependencies();
        }
        boolean available = browserCapabilityService.isChromiumAvailable();
        return List.of(
                new DependencyItem("chromium", "Chromium 内核", available, !available,
                        available ? "Chromium 内核已安装且可启动"
                                : "Chromium 内核未安装，扫码登录与 IP 同步不可用")
        );
    }

    private List<DependencyItem> checkLinuxDependencies() {
        List<DependencyItem> items = new ArrayList<>();
        boolean available = browserCapabilityService.isChromiumAvailable();

        if (available) {
            items.add(new DependencyItem("chromium", "Chromium 内核", true, false,
                    "Chromium 内核已安装且可启动"));
            return items;
        }

        String executablePath = resolveChromiumExecutablePath();
        if (executablePath == null) {
            items.add(new DependencyItem("chromium", "Chromium 内核", false, true,
                    "Chromium 浏览器未安装或无法定位可执行文件"));
            return items;
        }

        List<String> missingLibs = listMissingSharedLibraries(executablePath);
        if (missingLibs.isEmpty()) {
            items.add(new DependencyItem("chromium", "Chromium 内核", false, true,
                    "Chromium 内核无法启动，请查看后端日志定位具体原因"));
        } else {
            items.add(new DependencyItem("chromium", "Chromium 内核", false, true,
                    "Chromium 内核无法启动，缺少以下系统共享库"));
            for (String lib : missingLibs) {
                items.add(new DependencyItem("lib:" + lib, lib, false, true,
                        "未找到该共享库，请安装对应的系统依赖"));
            }
        }
        return items;
    }

    private String resolveChromiumExecutablePath() {
        try (Playwright playwright = Playwright.create()) {
            return playwright.chromium().executablePath();
        } catch (Exception e) {
            log.warn("[DependencyCheck] 无法定位 Chromium 可执行文件: {}", e.getMessage());
            return null;
        }
    }

    private List<String> listMissingSharedLibraries(String executablePath) {
        List<String> missing = new ArrayList<>();
        try {
            ProcessBuilder pb = new ProcessBuilder("ldd", executablePath);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(15, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("[DependencyCheck] ldd 执行超时");
                return missing;
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (output.contains("not a dynamic executable")) {
                log.warn("[DependencyCheck] ldd: 不是动态可执行文件");
                return missing;
            }
            for (String line : output.lines().toList()) {
                if (line.contains("not found")) {
                    String lib = line.split("=>")[0].trim();
                    if (!lib.isEmpty() && !missing.contains(lib)) {
                        missing.add(lib);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[DependencyCheck] ldd 检测失败: {}", e.getMessage());
        }
        return missing;
    }

    /**
     * 单个依赖项检测结果。
     */
    public record DependencyItem(String key, String name, boolean available, boolean installable, String message) {
    }

    private record CacheEntry(List<DependencyItem> items, Instant timestamp) {
    }
}
