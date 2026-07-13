package com.mattoid.scheduled.service.wecom;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mattoid.scheduled.entity.NotificationConfig;
import com.mattoid.scheduled.entity.WeComAppConfig;
import com.mattoid.scheduled.entity.WeComIpSyncLog;
import com.mattoid.scheduled.mapper.NotificationConfigMapper;
import com.mattoid.scheduled.util.CryptoUtil;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.Cookie;
import com.microsoft.playwright.options.WaitForSelectorState;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 企业微信可信 IP 白名单同步服务。
 * 通过 Playwright 浏览器自动化操作企业微信管理后台，实现：
 * 1. 扫码登录获取管理后台 Cookie
 * 2. 定时检测公网 IP 并同步到应用可信 IP 白名单
 *
 * 使用 Playwright 而非 Selenium：Playwright 不设置 navigator.webdriver，
 * 天然避免企业微信的自动化检测，与 MoviePilot dynamicwechat 插件（cloakbrowser/Playwright）一致。
 */
@Slf4j
@Service
public class WeComIpSyncService {

    private static final String WECOM_LOGIN_URL = "https://work.weixin.qq.com/wework_admin/loginpage_wx?from=myhome";
    private static final String WECOM_FRAME_URL = "https://work.weixin.qq.com/wework_admin/frame";
    private static final String WECOM_APP_URL_PREFIX = "https://work.weixin.qq.com/wework_admin/frame#apps/modApiApp/";
    private static final int QR_SESSION_TIMEOUT_SECONDS = 180;
    private static final int DEFAULT_SYNC_INTERVAL_MINUTES = 10;

    /**
     * 预设 IP 检测站点。key 为显示名称，value 为请求 URL。
     * 所有站点均通过{@link #parseIpFromResponse(String)} 通用规则解析。
     */
    public static final List<Map<String, String>> PRESET_IP_SOURCES = List.of(
            Map.of("label", "ipip.net", "url", "https://myip.ipip.net"),
            Map.of("label", "3322.net", "url", "https://ip.3322.net"),
            Map.of("label", "ifconfig.me", "url", "https://ifconfig.me/ip"),
            Map.of("label", "ipify.org", "url", "https://api.ipify.org"),
            Map.of("label", "ipinfo.io", "url", "https://ipinfo.io/ip"),
            Map.of("label", "icanhazip.com", "url", "https://icanhazip.com"),
            Map.of("label", "oray（花生壳）", "url", "https://ddns.oray.com/checkip"),
            Map.of("label", "AWS checkip", "url", "https://checkip.amazonaws.com")
    );

    /** JSON 响应中常见的 IP 字段名 */
    private static final List<String> JSON_IP_FIELDS = List.of(
            "ip", "query", "origin", "address", "ipAddress", "ip_address", "your_ip", "addr"
    );

    private static final Pattern IP_PATTERN = Pattern.compile("\\b(?:[0-9]{1,3}\\.){3}[0-9]{1,3}\\b");

    /** 查找二维码的 CSS 选择器列表，按优先级排列 */
    private static final List<String> QR_SELECTORS = List.of(
            "img.qrcode_login_img",
            "img[src*='qrcode']",
            "img[src*='qr_code']",
            ".qrcode img",
            ".qrcode_login_img",
            "img.login_qrcode_img",
            ".login_qrcode_panel img",
            ".wrp_code img"
    );

    private final NotificationConfigMapper notificationConfigMapper;
    private final WeComIpSyncLogService weComIpSyncLogService;
    private final WeComAdminAccountService weComAdminAccountService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 共享 Playwright 实例（线程安全，懒加载） */
    private volatile Playwright playwright;

    /** Playwright 非线程安全，并发同步需串行化浏览器操作 */
    private final Object browserLock = new Object();

    /** 二维码登录会话管理 */
    private final Map<String, QrLoginSession> qrSessions = new ConcurrentHashMap<>();

    /** 记录每个配置的最后同步时间 */
    private final Map<Long, Instant> lastSyncTimes = new ConcurrentHashMap<>();

    public WeComIpSyncService(NotificationConfigMapper notificationConfigMapper,
                              WeComIpSyncLogService weComIpSyncLogService,
                              WeComAdminAccountService weComAdminAccountService) {
        this.notificationConfigMapper = notificationConfigMapper;
        this.weComIpSyncLogService = weComIpSyncLogService;
        this.weComAdminAccountService = weComAdminAccountService;
    }

    @PreDestroy
    public void destroy() {
        // 清理所有未关闭的 QR 会话
        for (QrLoginSession session : qrSessions.values()) {
            closeSession(session);
        }
        qrSessions.clear();
        if (playwright != null) {
            try { playwright.close(); } catch (Exception ignored) {}
        }
    }

    private Playwright getPlaywright() {
        if (playwright == null) {
            synchronized (this) {
                if (playwright == null) {
                    playwright = Playwright.create();
                }
            }
        }
        return playwright;
    }

    // ==================== 浏览器创建 ====================

    /**
     * 创建 Playwright Browser 实例。
     * 使用 Playwright 自带 Chromium（版本兼容性有保证），不使用系统 Chrome，
     * 避免 Chrome 版本与 Playwright 协议不匹配导致 __adopt__ 错误。
     */
    private Browser createBrowser() {
        Playwright pw = getPlaywright();
        return pw.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(true)
                .setArgs(List.of(
                        "--lang=zh-CN",
                        "--no-sandbox",
                        "--disable-dev-shm-usage",
                        "--disable-gpu",
                        "--window-size=1920,1080"
                )));
    }

    /**
     * 创建带反检测配置的 BrowserContext。
     * Playwright 默认不设置 navigator.webdriver，无需额外隐藏。
     */
    private BrowserContext createContext(Browser browser) {
        return browser.newContext(new Browser.NewContextOptions()
                .setUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
                .setLocale("zh-CN")
                .setViewportSize(1920, 1080)
        );
    }

    // ==================== 二维码登录 ====================

    /**
     * 生成企业微信管理后台登录二维码。
     * 返回 sessionId 和二维码 Base64 图片数据。
     */
    public Map<String, String> generateLoginQrCode() {
        Browser browser = null;
        try {
            browser = createBrowser();
            BrowserContext context = createContext(browser);
            Page page = context.newPage();

            page.navigate(WECOM_LOGIN_URL, new Page.NavigateOptions().setTimeout(30000));
            page.waitForTimeout(3000);

            log.info("[QR] 页面标题: {}, URL: {}", page.title(), page.url());

            String qrBase64 = findAndCaptureQrCode(page);

            if (qrBase64 == null) {
                String pageTitle = page.title();
                String currentUrl = page.url();
                String pageSource = page.content();
                log.error("[QR] 未找到二维码。标题: {}, URL: {}, 页面长度: {}",
                        pageTitle, currentUrl, pageSource != null ? pageSource.length() : 0);
                try {
                    byte[] fullScreenshot = page.screenshot();
                    String b64 = Base64.getEncoder().encodeToString(fullScreenshot);
                    log.error("[QR] 全页截图(base64长度): {}", b64.length());
                    Map<String, String> debugResult = new HashMap<>();
                    debugResult.put("debug", "true");
                    debugResult.put("pageTitle", pageTitle);
                    debugResult.put("pageUrl", currentUrl);
                    debugResult.put("debugScreenshot", "data:image/png;base64," + b64);
                    closeBrowserQuietly(browser);
                    return debugResult;
                } catch (Exception ex) {
                    log.error("[QR] 调试截图也失败", ex);
                }
                closeBrowserQuietly(browser);
                throw new RuntimeException("未找到登录二维码，请检查网络或稍后重试");
            }

            // 保持 browser/context/page 存活，存到 session 中
            String sessionId = UUID.randomUUID().toString();
            QrLoginSession session = new QrLoginSession();
            session.sessionId = sessionId;
            session.browser = browser;
            session.context = context;
            session.page = page;
            session.createdAt = Instant.now();
            qrSessions.put(sessionId, session);

            log.info("[QR] 二维码生成成功: sessionId={}, base64长度={}", sessionId, qrBase64.length());

            Map<String, String> result = new HashMap<>();
            result.put("sessionId", sessionId);
            result.put("qrCodeBase64", qrBase64);
            return result;
        } catch (RuntimeException e) {
            if (browser != null) {
                closeBrowserQuietly(browser);
            }
            throw e;
        } catch (Exception e) {
            log.error("[QR] 生成企业微信登录二维码失败", e);
            if (browser != null) {
                closeBrowserQuietly(browser);
            }
            throw new RuntimeException("生成二维码失败: " + e.getMessage());
        }
    }

    /**
     * 在页面中查找二维码并截取为 Base64。
     * 先尝试在 iframe 中查找，再尝试在主页面查找。
     */
    private String findAndCaptureQrCode(Page page) {
        // 策略1: 在所有 frame 中查找
        List<Frame> frames = page.frames();
        log.info("[QR] 找到 {} 个 frame", frames.size());

        for (int i = 0; i < frames.size(); i++) {
            Frame frame = frames.get(i);
            try {
                Locator qrEl = findQrElement(frame);
                if (qrEl != null) {
                    byte[] screenshot = qrEl.screenshot();
                    if (screenshot != null && screenshot.length > 200) {
                        String base64 = "data:image/png;base64," + Base64.getEncoder().encodeToString(screenshot);
                        log.info("[QR] 在 frame[{}] 中找到二维码", i);
                        return base64;
                    }
                }
            } catch (Exception e) {
                log.debug("[QR] frame[{}] 中未找到二维码: {}", i, e.getMessage());
            }
        }

        // 策略2: 尝试截取登录区域
        try {
            Locator loginArea = page.locator(".login_panel, .login_qrcode_panel, .qrcode_login, .wrp_qrcode").first();
            if (loginArea.isVisible()) {
                byte[] screenshot = loginArea.screenshot();
                if (screenshot != null && screenshot.length > 200) {
                    log.info("[QR] 通过登录区域截图获取二维码");
                    return "data:image/png;base64," + Base64.getEncoder().encodeToString(screenshot);
                }
            }
        } catch (Exception e) {
            log.debug("[QR] 登录区域查找失败: {}", e.getMessage());
        }

        return null;
    }

    /**
     * 在当前 frame 中使用多个选择器尝试查找二维码元素。
     */
    private Locator findQrElement(Frame frame) {
        for (String selector : QR_SELECTORS) {
            try {
                Locator el = frame.locator(selector).first();
                if (el.isVisible(new Locator.IsVisibleOptions().setTimeout(1000))) {
                    log.debug("[QR] 选择器 '{}' 命中", selector);
                    return el;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * 检查二维码登录状态。
     * 用户扫码确认后，提取 Cookie 并返回。
     */
    public Map<String, Object> checkLoginStatus(String sessionId) {
        Map<String, Object> result = new HashMap<>();
        QrLoginSession session = qrSessions.get(sessionId);
        if (session == null) {
            result.put("status", "EXPIRED");
            return result;
        }

        if (Duration.between(session.createdAt, Instant.now()).getSeconds() > QR_SESSION_TIMEOUT_SECONDS) {
            cleanupSession(sessionId);
            result.put("status", "EXPIRED");
            return result;
        }

        try {
            Page page = session.page;
            String currentUrl = page.url();

            if (currentUrl != null && currentUrl.contains("wework_admin/frame")) {
                List<Cookie> cookies = session.context.cookies();
                StringBuilder cookieStr = new StringBuilder();
                for (Cookie cookie : cookies) {
                    if (cookie.domain != null && cookie.domain.contains("work.weixin.qq.com")) {
                        if (!cookieStr.isEmpty()) {
                            cookieStr.append("; ");
                        }
                        cookieStr.append(cookie.name).append("=").append(cookie.value);
                    }
                }

                result.put("status", "LOGGED_IN");
                result.put("cookie", cookieStr.toString());
                cleanupSession(sessionId);
                return result;
            }

            result.put("status", "WAITING");
            return result;
        } catch (Exception e) {
            log.warn("检查登录状态异常: sessionId={}", sessionId, e);
            result.put("status", "WAITING");
            return result;
        }
    }

    // ==================== IP 检测 ====================

    /**
     * 检测当前公网 IP 地址。
     */
    public String detectPublicIp(String configuredUrl) {
        IpDetectResult result = detectPublicIpWithSource(configuredUrl);
        return result != null ? result.ip() : null;
    }

    /**
     * 检测当前公网 IP，并返回实际生效的检测源。
     */
    public IpDetectResult detectPublicIpWithSource(String configuredUrl) {
        if (StringUtils.hasText(configuredUrl)) {
            String ip = fetchIpFromUrl(configuredUrl.trim());
            if (ip != null) {
                log.info("通过配置检测源获取到公网 IP: {} (来源: {})", ip, configuredUrl);
                return new IpDetectResult(ip, configuredUrl.trim());
            }
            log.warn("配置的检测源 {} 无法获取 IP，尝试预设站点", configuredUrl);
        }

        List<Map<String, String>> sources = new ArrayList<>(PRESET_IP_SOURCES);
        Collections.shuffle(sources);
        for (Map<String, String> source : sources) {
            String ip = fetchIpFromUrl(source.get("url"));
            if (ip != null) {
                log.info("通过预设站点获取到公网 IP: {} (来源: {})", ip, source.get("label"));
                return new IpDetectResult(ip, source.get("url"));
            }
        }
        log.error("所有 IP 检测源均不可用");
        return null;
    }

    /** IP 检测结果：解析出的公网 IP 与实际使用的检测源 URL。 */
    public record IpDetectResult(String ip, String source) {}

    /**
     * 从指定 URL 获取公网 IP。
     * 支持只输入域名（自动补全 https:// 前缀）。
     */
    private String fetchIpFromUrl(String url) {
        try {
            String normalizedUrl = normalizeUrl(url);
            HttpURLConnection conn = (HttpURLConnection) URI.create(normalizedUrl).toURL().openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36");
            conn.setRequestProperty("Accept", "text/plain, application/json, text/html, */*");
            String body = new String(conn.getInputStream().readAllBytes()).trim();
            if (body.isEmpty()) {
                return null;
            }
            return parseIpFromResponse(body);
        } catch (Exception e) {
            log.debug("IP 检测源 {} 不可用: {}", url, e.getMessage());
            return null;
        }
    }

    /**
     * 规范化 URL：如果只输入了域名则自动补全 https:// 前缀。
     */
    private String normalizeUrl(String url) {
        String trimmed = url.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return "https://" + trimmed;
        }
        return trimmed;
    }

    /**
     * 通用 IP 解析规则。
     */
    public String parseIpFromResponse(String body) {
        if (!StringUtils.hasText(body)) {
            return null;
        }
        String trimmed = body.trim();

        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                Object parsed = objectMapper.readValue(trimmed, Object.class);
                String ip = extractIpFromJson(parsed);
                if (ip != null) {
                    return ip;
                }
            } catch (Exception ignored) {}
        }

        if (IP_PATTERN.matcher(trimmed).matches()) {
            return trimmed;
        }

        Matcher matcher = IP_PATTERN.matcher(trimmed);
        if (matcher.find()) {
            return matcher.group();
        }

        return null;
    }

    /**
     * 从 JSON 对象中递归提取 IP 地址。
     */
    @SuppressWarnings("unchecked")
    private String extractIpFromJson(Object json) {
        if (json instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) json;
            for (String field : JSON_IP_FIELDS) {
                Object value = map.get(field);
                if (value instanceof String s) {
                    Matcher m = IP_PATTERN.matcher(s);
                    if (m.find()) {
                        return m.group();
                    }
                }
            }
            for (Object value : map.values()) {
                String ip = extractIpFromJson(value);
                if (ip != null) {
                    return ip;
                }
            }
        } else if (json instanceof List) {
            for (Object item : (List<Object>) json) {
                String ip = extractIpFromJson(item);
                if (ip != null) {
                    return ip;
                }
            }
        } else if (json instanceof String s) {
            Matcher m = IP_PATTERN.matcher(s);
            if (m.find()) {
                return m.group();
            }
        }
        return null;
    }

    // ==================== 可信 IP 同步 ====================

    /**
     * 解析管理后台 Cookie：优先从关联账户获取，回退到配置中直接存储的 Cookie（向后兼容）。
     */
    private String resolveAdminCookie(WeComAppConfig appConfig) {
        if (appConfig.getAdminAccountId() != null) {
            String cookie = weComAdminAccountService.getDecryptedCookie(appConfig.getAdminAccountId());
            if (StringUtils.hasText(cookie)) {
                return cookie;
            }
        }
        return CryptoUtil.decryptIfNeeded(appConfig.getAdminCookie());
    }

    /**
     * 执行指定配置的 IP 白名单同步（手动触发）。
     */
    public Map<String, Object> syncIpWhitelist(Long configId) {
        return syncIpWhitelist(configId, WeComIpSyncLog.TRIGGER_MANUAL);
    }

    /**
     * 执行指定配置的 IP 白名单同步，并记录同步日志。
     */
    public Map<String, Object> syncIpWhitelist(Long configId, String triggerType) {
        Map<String, Object> result = new HashMap<>();

        // 加载配置（轻量操作，在创建日志前完成前置检查）
        NotificationConfig nc = notificationConfigMapper.selectById(configId);
        if (nc == null) {
            result.put("success", false);
            result.put("message", "配置不存在: " + configId);
            return result;
        }
        WeComAppConfig appConfig;
        try {
            appConfig = objectMapper.readValue(nc.getConfigJson(), WeComAppConfig.class);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "配置解析失败: " + e.getMessage());
            return result;
        }

        // 自动触发：检查间隔时间，未到达则直接跳过，不执行后续任何逻辑
        if (WeComIpSyncLog.TRIGGER_AUTO.equals(triggerType)) {
            int intervalMinutes = appConfig.getSyncIntervalMinutes() != null
                    ? appConfig.getSyncIntervalMinutes() : DEFAULT_SYNC_INTERVAL_MINUTES;
            Instant lastSync = lastSyncTimes.get(configId);
            if (lastSync != null
                    && Duration.between(lastSync, Instant.now()).toMinutes() < intervalMinutes) {
                log.debug("configId={} 距离上次同步未满 {} 分钟，跳过", configId, intervalMinutes);
                result.put("success", true);
                result.put("message", "距离上次同步未满 " + intervalMinutes + " 分钟，跳过");
                result.put("skipped", true);
                return result;
            }
        }

        // 前置条件：应用 URL 或应用 ID 必须配置
        if (!StringUtils.hasText(appConfig.getAppManageUrl())
                && appConfig.getAgentId() == null) {
            result.put("success", true);
            result.put("message", "应用 URL 和应用 ID 均未配置，跳过");
            result.put("skipped", true);
            return result;
        }

        // 前置检查通过，创建同步日志并开始正式流程
        WeComIpSyncLog syncLog = new WeComIpSyncLog();
        syncLog.setConfigId(configId);
        syncLog.setConfigName(nc.getConfigName());
        syncLog.setTriggerType(triggerType);
        syncLog.setStartTime(LocalDateTime.now());
        long t0 = System.currentTimeMillis();
        Browser browser = null;
        try {
            String adminCookie = resolveAdminCookie(appConfig);
            if (!StringUtils.hasText(adminCookie)) {
                return failResult(result, syncLog, WeComIpSyncLog.FAIL_COOKIE_MISSING,
                        "管理后台 Cookie 未配置", t0);
            }

            // 检测公网 IP
            IpDetectResult detected = detectPublicIpWithSource(appConfig.getIpDetectionUrl());
            if (detected == null) {
                return failResult(result, syncLog, WeComIpSyncLog.FAIL_IP_DETECT,
                        "无法检测公网 IP：所有检测源均不可用或响应中未解析到 IPv4 地址", t0);
            }
            List<String> detectedIps = List.of(detected.ip());
            syncLog.setDetectedIp(detected.ip());
            syncLog.setIpSource(detected.source());

            // 启动浏览器，注入 Cookie（Playwright 非线程安全，串行化浏览器操作）
            synchronized (browserLock) {
                browser = createBrowser();
                BrowserContext context = createContext(browser);
                injectCookies(context, adminCookie);
                Page page = context.newPage();

                // 应用管理页 URL：优先使用配置的 URL，否则按应用 ID 拼接
                String appUrl = resolveAppUrl(appConfig);

                // 访问应用管理页面
                navigateToAppPage(page, appUrl);

                // 打印页面状态，便于排查
                String landedUrl = page.url();
                String landedTitle = page.title();
                log.info("[SYNC] configId={}, 落地页标题: [{}], URL: {}", configId, landedTitle, landedUrl);

                // Cookie 失效时会被重定向到登录页
                if (isLoginPage(page)) {
                    log.warn("[SYNC] configId={}, Cookie 已失效，页面跳转到登录页。标题: [{}], URL: {}",
                            configId, landedTitle, landedUrl);
                    return failResult(result, syncLog, WeComIpSyncLog.FAIL_COOKIE_INVALID,
                            "管理后台 Cookie 已失效，请重新扫码获取", t0);
                }

                // 点击「配置」按钮
                Locator configBtn = findConfigButton(page, configId);
                if (configBtn == null) {
                    String debugBase64 = "";
                    try {
                        debugBase64 = Base64.getEncoder().encodeToString(page.screenshot());
                    } catch (Exception ignored) {}
                    String pageSrc = page.content();
                    String srcSnippet = pageSrc != null && pageSrc.length() > 500
                            ? pageSrc.substring(0, 500) : String.valueOf(pageSrc);
                    log.error("[SYNC] configId={}, 未找到「配置」按钮。标题: [{}], URL: {}, 截图长度: {}, 页面片段: {}",
                            configId, page.title(), page.url(), debugBase64.length(), srcSnippet);
                    return failResult(result, syncLog, WeComIpSyncLog.FAIL_SYNC,
                            "未找到「配置」按钮，请确认应用管理页 URL（或应用 ID）正确且 Cookie 有效。页面标题: " + page.title(), t0);
                }
                configBtn.scrollIntoViewIfNeeded();
                page.waitForTimeout(500);
            try {
                configBtn.click(new Locator.ClickOptions().setTimeout(5000));
            } catch (Exception e) {
                log.debug("[SYNC] configId={}, 常规 click 失败，尝试 force click", configId);
                configBtn.click(new Locator.ClickOptions().setForce(true));
            }

            // 等待 textarea 出现
            Locator textarea = page.locator("textarea.js_ipConfig_textarea");
            textarea.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE).setTimeout(10000));

            // 读取当前可信 IP
            String currentIpsText = textarea.inputValue();
            List<String> currentIps = parseIpList(currentIpsText);
            syncLog.setOldIps(currentIpsText);
            log.info("当前可信 IP 列表: {}", currentIps);

            // 所有 WAN 探测 IP 均已在白名单中，无需更新（跳过逻辑不落日志）
            if (currentIps.containsAll(detectedIps)) {
                log.info("探测 IP {} 均已在可信 IP 白名单 {} 中，跳过本次同步", detectedIps, currentIps);
                closeDialog(page);
                recordAutoSyncTime(configId, triggerType);
                result.put("success", true);
                result.put("message", "探测 IP 已在白名单中，跳过");
                result.put("skipped", true);
                return result;
            }

            // 计算新的 IP 列表
            List<String> newIps = computeNewIpList(currentIps, detectedIps);
            String newIpsText = String.join(";", newIps);

            syncLog.setNewIps(newIpsText);
            log.info("新增可信 IP: {} (现有: {} -> 更新后: {})", detectedIps, currentIpsText, newIpsText);

            // 填入新 IP 并确认
            textarea.fill(newIpsText);
            Locator confirmBtn = page.locator(".js_ipConfig_confirmBtn");
            confirmBtn.click();
            page.waitForTimeout(2000);

                recordAutoSyncTime(configId, triggerType);
                result.put("success", true);
                result.put("message", "可信 IP 已更新: " + newIpsText);
                result.put("currentIps", detectedIps);
                result.put("newIps", newIps);
                finishLog(syncLog, WeComIpSyncLog.STATUS_SUCCESS, null,
                        (String) result.get("message"), t0);
                return result;
            } // end synchronized (browserLock)

        } catch (Exception e) {
            log.error("同步可信 IP 失败: configId={}", configId, e);
            String failReason = browser != null && isLoginPageSafe(browser)
                    ? WeComIpSyncLog.FAIL_COOKIE_INVALID : WeComIpSyncLog.FAIL_SYNC;
            String message = WeComIpSyncLog.FAIL_COOKIE_INVALID.equals(failReason)
                    ? "管理后台 Cookie 已失效，请重新扫码获取" : "同步失败: " + e.getMessage();
            return failResult(result, syncLog, failReason, message, t0);
        } finally {
            if (browser != null) {
                closeBrowserQuietly(browser);
            }
        }
    }

    /** 自动触发时记录同步时间，用于间隔检查。 */
    private void recordAutoSyncTime(Long configId, String triggerType) {
        if (WeComIpSyncLog.TRIGGER_AUTO.equals(triggerType)) {
            lastSyncTimes.put(configId, Instant.now());
        }
    }

    /** 解析应用管理页 URL：优先使用配置的 URL，否则按应用 ID 拼接。 */
    private String resolveAppUrl(WeComAppConfig appConfig) {
        if (StringUtils.hasText(appConfig.getAppManageUrl())) {
            return appConfig.getAppManageUrl().trim();
        }
        return WECOM_APP_URL_PREFIX + appConfig.getAgentId();
    }

    /**
     * 导航到应用管理页。
     * 企业微信后台是 hash 路由 SPA，直接导航到带 hash 的 URL 让 SPA 在初始化时读取路由。
     */
    private void navigateToAppPage(Page page, String appUrl) {
        // 直接导航到完整 URL（含 hash），让 SPA 初始化时读取路由
        page.navigate(appUrl, new Page.NavigateOptions().setTimeout(30000));
        // 等待网络空闲（SPA 数据请求完成），比固定 sleep 更可靠
        try {
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE,
                    new Page.WaitForLoadStateOptions().setTimeout(15000));
        } catch (Exception e) {
            log.debug("[SYNC] 等待 NETWORKIDLE 超时（可忽略）: {}", e.getMessage());
        }
        // 额外等待确保 SPA 渲染完成
        page.waitForTimeout(3000);
    }

    /**
     * 检测企业微信管理后台 Cookie 是否有效。
     */
    public Map<String, Object> checkCookieValid(Long configId, Integer agentId, String adminCookie, String appManageUrl) {
        Map<String, Object> result = new HashMap<>();
        String cookie = adminCookie;
        String targetUrl = appManageUrl;
        try {
            if (configId != null) {
                NotificationConfig nc = notificationConfigMapper.selectById(configId);
                if (nc == null) {
                    result.put("valid", false);
                    result.put("message", "配置不存在: " + configId);
                    return result;
                }
                WeComAppConfig appConfig = objectMapper.readValue(nc.getConfigJson(), WeComAppConfig.class);
                cookie = resolveAdminCookie(appConfig);
                targetUrl = resolveAppUrl(appConfig);
            } else {
                cookie = CryptoUtil.decryptIfNeeded(cookie);
                if (!StringUtils.hasText(targetUrl) && agentId != null) {
                    targetUrl = WECOM_APP_URL_PREFIX + agentId;
                }
            }
        } catch (Exception e) {
            result.put("valid", false);
            result.put("message", "读取配置失败: " + e.getMessage());
            return result;
        }

        if (!StringUtils.hasText(cookie)) {
            result.put("valid", false);
            result.put("message", "管理后台 Cookie 未配置");
            return result;
        }

        Browser browser = null;
        synchronized (browserLock) {
            try {
                browser = createBrowser();
                BrowserContext context = createContext(browser);
                injectCookies(context, cookie);
                Page page = context.newPage();
                navigateToAppPage(page, StringUtils.hasText(targetUrl)
                        ? targetUrl.trim() : WECOM_FRAME_URL);

                if (isLoginPageDeep(page)) {
                    result.put("valid", false);
                    result.put("message", "Cookie 已失效，请重新扫码获取");
                    return result;
                }
                result.put("valid", true);
                result.put("message", "Cookie 有效");
                return result;
            } catch (Exception e) {
                log.warn("检测 Cookie 有效性异常: configId={}", configId, e);
                result.put("valid", false);
                result.put("message", "检测失败: " + e.getMessage());
                return result;
            } finally {
                if (browser != null) {
                    closeBrowserQuietly(browser);
                }
            }
        } // end synchronized (browserLock)
    }

    /**
     * 多策略查找「配置」按钮。
     * 使用 MoviePilot 插件验证过的完整 XPath 作为首选策略。
     */
    private Locator findConfigButton(Page page, Long configId) {
        // 策略1: MoviePilot 插件验证过的完整 XPath（含 _mod_card_operationLink 类）
        String primaryXpath = "//div[contains(@class, 'js_show_ipConfig_dialog')]//a[contains(@class, '_mod_card_operationLink') and text()='配置']";
        try {
            Locator btn = page.locator("xpath=" + primaryXpath);
            btn.waitFor(new Locator.WaitForOptions()
                    .setState(WaitForSelectorState.VISIBLE).setTimeout(10000));
            log.info("[SYNC] configId={}, 找到「配置」按钮（MoviePilot XPath）", configId);
            return btn;
        } catch (Exception e) {
            log.debug("[SYNC] configId={}, MoviePilot XPath 未命中: {}", configId, e.getMessage());
        }

        // 策略2: 宽松 XPath 回退
        String[] fallbackXpaths = {
                "//div[contains(@class, 'js_show_ipConfig_dialog')]//a[text()='配置']",
                "//div[contains(@class, 'js_show_ipConfig_dialog')]//a[contains(text(),'配置')]",
                "//a[contains(@class, 'js_show_ipConfig_dialog')]",
                "//span[contains(@class, 'js_show_ipConfig_dialog')]//a",
                "//div[contains(@class,'js_ipConfig')]//a[contains(text(),'配置')]",
                "//a[text()='配置']",
        };
        for (String xp : fallbackXpaths) {
            try {
                Locator btn = page.locator("xpath=" + xp).first();
                if (btn.isVisible(new Locator.IsVisibleOptions().setTimeout(2000))) {
                    log.info("[SYNC] configId={}, 找到「配置」按钮，XPath: {}", configId, xp);
                    return btn;
                }
            } catch (Exception ignored) {}
        }

        // 策略3: CSS 选择器
        String[] cssSelectors = {
                ".js_show_ipConfig_dialog a",
                ".js_show_ipConfig_dialog",
                "a.js_show_ipConfig_dialog",
        };
        for (String css : cssSelectors) {
            try {
                Locator btn = page.locator(css).first();
                if (btn.isVisible(new Locator.IsVisibleOptions().setTimeout(2000))) {
                    log.info("[SYNC] configId={}, 找到「配置」按钮，CSS: {}", configId, css);
                    return btn;
                }
            } catch (Exception ignored) {}
        }

        // 策略4: 文本精确匹配
        try {
            Locator btn = page.getByText("配置", new Page.GetByTextOptions().setExact(true)).first();
            if (btn.isVisible(new Locator.IsVisibleOptions().setTimeout(2000))) {
                log.info("[SYNC] configId={}, 通过文本匹配找到「配置」按钮", configId);
                return btn;
            }
        } catch (Exception ignored) {}

        // 策略5: 等待更长时间后重试（某些环境渲染慢）
        try {
            log.info("[SYNC] configId={}, 所有快速策略未命中，等待 5s 后重试", configId);
            page.waitForTimeout(5000);
            Locator btn = page.locator("xpath=" + primaryXpath);
            if (btn.isVisible(new Locator.IsVisibleOptions().setTimeout(3000))) {
                log.info("[SYNC] configId={}, 延迟后找到「配置」按钮", configId);
                return btn;
            }
        } catch (Exception ignored) {}

        return null;
    }

    /** 判断浏览器当前是否停留在企业微信登录页（Cookie 失效特征）。 */
    private boolean isLoginPage(Page page) {
        try {
            String url = page.url();
            if (url != null && (url.contains("loginpage") || url.contains("/login"))) {
                return true;
            }
            String title = page.title();
            if (title != null && (title.contains("登录") || title.contains("login") || title.contains("Login"))) {
                return true;
            }
            String src = page.content();
            if (src != null) {
                if (src.contains("qrcode_login") || src.contains("loginpage_wx")
                        || src.contains("login_qrcode") || src.contains("wrp_code")) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.debug("[SYNC] 检查登录页异常: {}", e.getMessage());
        }
        return false;
    }

    /**
     * 深度判断是否为登录页：除主文档外，还逐一检查 iframe 内部。
     */
    private boolean isLoginPageDeep(Page page) {
        if (isLoginPage(page)) {
            return true;
        }
        try {
            List<Frame> frames = page.frames();
            for (int i = 0; i < frames.size(); i++) {
                try {
                    String src = frames.get(i).content();
                    if (src != null && (src.contains("qrcode_login") || src.contains("loginpage_wx")
                            || src.contains("login_qrcode") || src.contains("wrp_code")
                            || src.contains("扫码登录"))) {
                        log.info("[SYNC] 在 frame[{}] 内检测到登录页特征", i);
                        return true;
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            log.debug("[SYNC] frame 登录页检查异常: {}", e.getMessage());
        }
        return false;
    }

    /** 安全地检查 browser 中的 page 是否为登录页（用于 catch 块中兜底判断）。 */
    private boolean isLoginPageSafe(Browser browser) {
        try {
            for (BrowserContext ctx : browser.contexts()) {
                for (Page page : ctx.pages()) {
                    if (isLoginPage(page)) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    /** 组装失败返回值并落日志（同一配置、同一失败原因一小时内只记录一条，避免定时任务刷屏）。 */
    private Map<String, Object> failResult(Map<String, Object> result, WeComIpSyncLog syncLog,
                                           String failReason, String message, long t0) {
        result.put("success", false);
        result.put("message", message);
        result.put("failReason", failReason);
        if (!weComIpSyncLogService.hasRecentFailure(syncLog.getConfigId(), failReason)) {
            finishLog(syncLog, WeComIpSyncLog.STATUS_FAIL, failReason, message, t0);
        } else {
            log.info("配置 {} 一小时内已有相同原因({})的失败记录，本次失败不再落日志: {}",
                    syncLog.getConfigId(), failReason, message);
        }
        return result;
    }

    /** 补全日志收尾字段并落库。 */
    private void finishLog(WeComIpSyncLog syncLog, String status, String failReason,
                           String message, long t0) {
        syncLog.setStatus(status);
        syncLog.setFailReason(failReason);
        syncLog.setMessage(message);
        syncLog.setDurationMs(System.currentTimeMillis() - t0);
        syncLog.setEndTime(LocalDateTime.now());
        weComIpSyncLogService.record(syncLog);
    }

    /**
     * 执行所有已启用自动同步的配置。
     */
    public void syncAllEnabledConfigs() {
        List<NotificationConfig> configs = notificationConfigMapper.selectList(null);
        for (NotificationConfig nc : configs) {
            if (!"WECOM_APP".equals(nc.getConfigType())) {
                continue;
            }
            if (nc.getStatus() == null || nc.getStatus() != 1) {
                continue;
            }
            try {
                WeComAppConfig appConfig = objectMapper.readValue(nc.getConfigJson(), WeComAppConfig.class);
                if (appConfig.getAutoSyncIp() == null || !appConfig.getAutoSyncIp()) {
                    continue;
                }
                if (!StringUtils.hasText(appConfig.getAppManageUrl())
                        && appConfig.getAgentId() == null) {
                    continue;
                }
                if (!StringUtils.hasText(resolveAdminCookie(appConfig))) {
                    continue;
                }

                log.info("开始自动同步可信 IP: configId={}, configName={}", nc.getId(), nc.getConfigName());
                Map<String, Object> syncResult = syncIpWhitelist(nc.getId(), WeComIpSyncLog.TRIGGER_AUTO);
                log.info("自动同步可信 IP 完成: configId={}, result={}", nc.getId(), syncResult.get("message"));
            } catch (Exception e) {
                log.error("自动同步可信 IP 异常: configId={}", nc.getId(), e);
            }
        }
    }

    // ==================== 内部方法 ====================

    /**
     * 计算新的 IP 白名单。
     * <p>规则：保留原始白名单的第一个 IP（基础 IP 不可移除），
     * 然后与 WAN 探测 IP 列表合并。白名单中非第一个且不在 WAN 列表中的 IP 会被移除。</p>
     * <p>例：白名单 [A B C D]，WAN [a C d] → 结果 [A a C d]。
     * 白名单 [A B C D]，WAN [a C] → 结果 [A a C]。</p>
     */
    private List<String> computeNewIpList(List<String> currentIps, List<String> detectedIps) {
        List<String> result = new ArrayList<>();
        // 保留原始白名单的第一个 IP
        if (!currentIps.isEmpty()) {
            result.add(currentIps.get(0));
        }
        // 遍历 WAN 探测 IP 列表，追加不在结果中的
        for (String ip : detectedIps) {
            if (!result.contains(ip)) {
                result.add(ip);
            }
        }
        return result;
    }

    /**
     * 解析 IP 列表文本（分号或换行分隔）。
     */
    private List<String> parseIpList(String text) {
        if (!StringUtils.hasText(text)) {
            return new ArrayList<>();
        }
        List<String> ips = new ArrayList<>();
        for (String part : text.split("[;\\n\\r]+")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty() && IP_PATTERN.matcher(trimmed).matches()) {
                ips.add(trimmed);
            }
        }
        return ips;
    }

    /**
     * 关闭配置弹窗（按 Escape）。
     */
    private void closeDialog(Page page) {
        try {
            page.keyboard().press("Escape");
        } catch (Exception e) {
            log.debug("关闭弹窗失败（可忽略）: {}", e.getMessage());
        }
    }

    /**
     * 向 BrowserContext 注入 Cookie。
     */
    private void injectCookies(BrowserContext context, String cookieString) {
        if (!StringUtils.hasText(cookieString)) {
            return;
        }
        List<Cookie> cookies = new ArrayList<>();
        for (String part : cookieString.split(";")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            int eq = trimmed.indexOf('=');
            if (eq <= 0) continue;
            String name = trimmed.substring(0, eq).trim();
            String value = trimmed.substring(eq + 1).trim();
            try {
                cookies.add(new Cookie(name, value)
                        .setDomain(".work.weixin.qq.com")
                        .setPath("/"));
            } catch (Exception e) {
                log.debug("注入 Cookie 失败: {}={} - {}", name, value, e.getMessage());
            }
        }
        if (!cookies.isEmpty()) {
            context.addCookies(cookies);
        }
    }

    /**
     * 下载图片并转为 Base64。
     */
    private String downloadImageAsBase64(String imageUrl) throws IOException {
        URL url = URI.create(imageUrl).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("Referer", "https://work.weixin.qq.com/");
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36");
        try (InputStream is = conn.getInputStream()) {
            byte[] data = is.readAllBytes();
            return "data:image/png;base64," + Base64.getEncoder().encodeToString(data);
        }
    }

    /**
     * 清理二维码登录会话。
     */
    private void cleanupSession(String sessionId) {
        QrLoginSession session = qrSessions.remove(sessionId);
        if (session != null) {
            closeSession(session);
        }
    }

    private void closeSession(QrLoginSession session) {
        if (session == null) return;
        if (session.page != null) {
            try { session.page.close(); } catch (Exception ignored) {}
        }
        if (session.context != null) {
            try { session.context.close(); } catch (Exception ignored) {}
        }
        if (session.browser != null) {
            try { session.browser.close(); } catch (Exception ignored) {}
        }
    }

    private void closeBrowserQuietly(Browser browser) {
        try { browser.close(); } catch (Exception ignored) {}
    }

    /**
     * 清理过期的二维码登录会话。
     */
    public void cleanupExpiredSessions() {
        Instant now = Instant.now();
        qrSessions.entrySet().removeIf(entry -> {
            QrLoginSession session = entry.getValue();
            if (Duration.between(session.createdAt, now).getSeconds() > QR_SESSION_TIMEOUT_SECONDS) {
                closeSession(session);
                return true;
            }
            return false;
        });
    }

    // ==================== 免登录打开管理后台（有头浏览器） ====================

    /**
     * 启动带 Cookie 的有头浏览器，在本机弹出已登录的企业微信管理后台窗口。
     * 浏览器窗口由用户手动关闭，关闭后自动回收资源。
     */
    public void openAdminBrowser(String adminCookie) {
        Thread t = new Thread(() -> {
            Browser browser = null;
            try {
                Playwright pw = getPlaywright();
                browser = pw.chromium().launch(new BrowserType.LaunchOptions()
                        .setHeadless(false)
                        .setArgs(List.of(
                                "--lang=zh-CN",
                                "--no-sandbox",
                                "--disable-dev-shm-usage",
                                "--start-maximized"
                        )));
                BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                        .setUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
                        .setLocale("zh-CN")
                        .setViewportSize(null)
                );
                injectCookies(context, adminCookie);
                Page page = context.newPage();
                page.navigate("https://work.weixin.qq.com/wework_admin/frame");
                log.info("已打开企微管理后台浏览器窗口");
            } catch (Exception e) {
                log.error("打开企微管理后台失败", e);
                if (browser != null) {
                    try { browser.close(); } catch (Exception ignored) {}
                }
            }
        }, "wecom-admin-browser");
        t.setDaemon(true);
        t.start();
    }

    /** 二维码登录会话 */
    private static class QrLoginSession {
        String sessionId;
        Browser browser;
        BrowserContext context;
        Page page;
        Instant createdAt;
    }
}
