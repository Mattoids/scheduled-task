package com.mattoid.scheduled.task;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mattoid.scheduled.entity.TaskWebCrawlConfig;
import com.mattoid.scheduled.util.PlaceholderUtils;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.devtools.Command;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
public class WebDriverManager {

    private static final int DEFAULT_WAIT_SECONDS = 30;

    private final ObjectMapper objectMapper;

    public WebDriverManager(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Document fetchPage(TaskWebCrawlConfig config, String url, Map<String, Object> params) throws Exception {
        Map<String, Object> driverConfig = parseDriverConfig(replaceRequestVariables(config.getDriverConfig(), params));
        ChromeOptions options = buildChromeOptions(config, driverConfig, url);
        ChromeDriver driver = new ChromeDriver(options);
        DevTools devTools = driver.getDevTools();
        devTools.createSession();
        try {
            Map<String, String> headers = buildHeaders(config, params);
            if (!headers.isEmpty()) {
                devTools.send(new Command<>("Network.enable", Collections.emptyMap()));
                devTools.send(new Command<>("Network.setExtraHTTPHeaders", Collections.singletonMap("headers", headers)));
            }

            driver.get(url);
            applyCookies(driver, config, params);

            Integer waitSeconds = (Integer) driverConfig.getOrDefault("waitSeconds", DEFAULT_WAIT_SECONDS);
            String waitSelector = (String) driverConfig.get("waitSelector");
            if (StringUtils.hasText(waitSelector)) {
                new WebDriverWait(driver, Duration.ofSeconds(waitSeconds))
                        .until(ExpectedConditions.presenceOfElementLocated(org.openqa.selenium.By.cssSelector(waitSelector)));
            } else {
                Thread.sleep(1000);
            }

            Integer extraWaitMs = (Integer) driverConfig.getOrDefault("extraWaitMs", 0);
            if (extraWaitMs != null && extraWaitMs > 0) {
                Thread.sleep(extraWaitMs);
            }

            String pageSource = driver.getPageSource();
            return Jsoup.parse(pageSource, resolveBaseUrl(url));
        } finally {
            driver.quit();
        }
    }

    private ChromeOptions buildChromeOptions(TaskWebCrawlConfig config, Map<String, Object> driverConfig, String url) {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=" + driverConfig.getOrDefault("windowSize", "1920,1080"));

        String proxyArg = buildProxyArg(config, driverConfig);
        if (StringUtils.hasText(proxyArg)) {
            options.addArguments("--proxy-server=" + proxyArg);
        }

        String browserPath = (String) driverConfig.get("browserPath");
        if (StringUtils.hasText(browserPath)) {
            options.setBinary(browserPath);
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> prefs = (Map<String, Object>) driverConfig.get("prefs");
        if (prefs != null) {
            options.setExperimentalOption("prefs", prefs);
        }

        applyCustomArgs(options, driverConfig);

        if (url != null && url.toLowerCase().startsWith("https")) {
            options.addArguments("--ignore-certificate-errors",
                    "--ignore-certificate-errors-spki-list",
                    "--allow-insecure-localhost");
        }

        return options;
    }

    private void applyCustomArgs(ChromeOptions options, Map<String, Object> driverConfig) {
        @SuppressWarnings("unchecked")
        Map<String, Object> args = (Map<String, Object>) driverConfig.get("args");
        if (args == null) {
            return;
        }
        args.forEach((key, value) -> {
            String name = String.valueOf(key);
            String argName = name.startsWith("--") ? name : "--" + name;
            if (value instanceof Boolean b) {
                if (Boolean.TRUE.equals(b)) {
                    options.addArguments(argName);
                }
                return;
            }
            if (value == null) {
                options.addArguments(argName);
                return;
            }
            String str = String.valueOf(value);
            if (str.isEmpty()) {
                options.addArguments(argName);
            } else {
                options.addArguments(argName + "=" + str);
            }
        });
    }

    private Map<String, String> buildHeaders(TaskWebCrawlConfig config, Map<String, Object> params) {
        Map<String, String> headers = parseJsonMap(replaceRequestVariables(config.getRequestHeaders(), params));
        applyAuthHeaders(config, params, headers);
        return headers;
    }

    private void applyAuthHeaders(TaskWebCrawlConfig config, Map<String, Object> params, Map<String, String> headers) {
        String authType = config.getAuthType();
        if (!StringUtils.hasText(authType) || "NONE".equalsIgnoreCase(authType)) {
            return;
        }
        String authConfig = replaceRequestVariables(config.getAuthConfig(), params);
        switch (authType.toUpperCase()) {
            case "BASIC" -> applyBasicAuthHeader(authConfig, headers);
            case "TOKEN" -> applyTokenAuthHeader(authConfig, headers);
            case "OAUTH2" -> applyOAuth2AuthHeader(authConfig, headers);
            case "FORM" -> log.warn("FORM 认证在动态渲染模式下建议通过 cookies 字段配置登录后的 Cookie");
            default -> log.warn("不支持的认证类型: {}", authType);
        }
    }

    private void applyBasicAuthHeader(String authConfig, Map<String, String> headers) {
        if (!StringUtils.hasText(authConfig)) {
            return;
        }
        Map<String, String> auth = parseJsonMap(authConfig, false);
        if (!auth.isEmpty()) {
            String username = auth.get("username");
            String password = auth.get("password");
            if (StringUtils.hasText(username)) {
                String credentials = Base64.getEncoder()
                        .encodeToString((username + ":" + (password != null ? password : "")).getBytes(StandardCharsets.UTF_8));
                headers.put("Authorization", "Basic " + credentials);
            }
            return;
        }
        String value = authConfig.trim();
        if (value.toUpperCase().startsWith("BASIC ")) {
            headers.put("Authorization", value);
        } else {
            headers.put("Authorization", "Basic " + value);
        }
    }

    private void applyTokenAuthHeader(String authConfig, Map<String, String> headers) {
        if (!StringUtils.hasText(authConfig)) {
            return;
        }
        Map<String, String> auth = parseJsonMap(authConfig, false);
        String token;
        String headerName;
        String prefix;
        if (!auth.isEmpty()) {
            token = auth.get("token");
            headerName = auth.getOrDefault("headerName", "Authorization");
            prefix = auth.getOrDefault("prefix", "Bearer");
        } else {
            token = authConfig.trim();
            headerName = "Authorization";
            prefix = "Bearer";
        }
        if (StringUtils.hasText(token)) {
            headers.put(headerName, (prefix + " " + token).trim());
        }
    }

    private void applyOAuth2AuthHeader(String authConfig, Map<String, String> headers) {
        Map<String, String> auth = parseJsonMap(authConfig, false);
        String token = auth.get("accessToken");
        if (StringUtils.hasText(token)) {
            headers.put("Authorization", "Bearer " + token);
        }
    }

    private void applyCookies(ChromeDriver driver, TaskWebCrawlConfig config, Map<String, Object> params) {
        String cookiesValue = replaceRequestVariables(config.getCookies(), params);
        Map<String, String> cookies = parseCookieMap(cookiesValue);
        if (cookies.isEmpty()) {
            return;
        }
        boolean added = false;
        for (Map.Entry<String, String> entry : cookies.entrySet()) {
            try {
                driver.manage().addCookie(new Cookie(entry.getKey(), entry.getValue()));
                added = true;
            } catch (Exception e) {
                log.warn("动态渲染添加 Cookie 失败: {} - {}", entry.getKey(), e.getMessage());
            }
        }
        if (added) {
            driver.navigate().refresh();
        }
    }

    private Map<String, String> parseCookieMap(String value) {
        Map<String, String> cookies = new LinkedHashMap<>();
        if (!StringUtils.hasText(value)) {
            return cookies;
        }
        try {
            Map<String, Object> map = objectMapper.readValue(value, new TypeReference<Map<String, Object>>() {
            });
            map.forEach((k, v) -> cookies.put(k, v != null ? String.valueOf(v) : ""));
            return cookies;
        } catch (Exception ignored) {
        }
        for (String part : value.split(";")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int eq = trimmed.indexOf('=');
            if (eq > 0) {
                cookies.put(trimmed.substring(0, eq), trimmed.substring(eq + 1));
            } else {
                cookies.put(trimmed, "");
            }
        }
        return cookies;
    }

    private Map<String, String> parseJsonMap(String json) {
        return parseJsonMap(json, true);
    }

    private Map<String, String> parseJsonMap(String json, boolean logWarning) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> map = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
            Map<String, String> result = new LinkedHashMap<>();
            map.forEach((k, v) -> result.put(k, v != null ? String.valueOf(v) : ""));
            return result;
        } catch (Exception e) {
            if (logWarning) {
                log.warn("解析 JSON 失败: {}", e.getMessage());
            }
            return new LinkedHashMap<>();
        }
    }

    private String replaceRequestVariables(String text, Map<String, Object> params) {
        return PlaceholderUtils.replacePlaceholders(text, params);
    }

    private String buildProxyArg(TaskWebCrawlConfig config, Map<String, Object> driverConfig) {
        if (WebCrawlProxyHelper.isProxyEnabled(config)) {
            StringBuilder sb = new StringBuilder("http://");
            if (StringUtils.hasText(config.getProxyUsername()) && StringUtils.hasText(config.getProxyPassword())) {
                sb.append(config.getProxyUsername()).append(":").append(config.getProxyPassword()).append("@");
            }
            sb.append(config.getProxyHost()).append(":").append(config.getProxyPort());
            return sb.toString();
        }
        return (String) driverConfig.get("proxy");
    }

    private Map<String, Object> parseDriverConfig(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            log.warn("解析 driverConfig 失败: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private String resolveBaseUrl(String url) {
        try {
            return new URL(url).toString();
        } catch (MalformedURLException e) {
            return url;
        }
    }
}
