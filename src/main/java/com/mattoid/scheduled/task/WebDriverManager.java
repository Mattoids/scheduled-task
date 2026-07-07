package com.mattoid.scheduled.task;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mattoid.scheduled.entity.TaskWebCrawlConfig;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
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
        Map<String, Object> driverConfig = parseDriverConfig(config.getDriverConfig());
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=" + driverConfig.getOrDefault("windowSize", "1920,1080"));

        String proxy = (String) driverConfig.get("proxy");
        if (StringUtils.hasText(proxy)) {
            options.addArguments("--proxy-server=" + proxy);
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

        @SuppressWarnings("unchecked")
        Map<String, Object> args = (Map<String, Object>) driverConfig.get("args");
        if (args != null) {
            args.keySet().forEach(key -> options.addArguments(String.valueOf(key)));
        }

        WebDriver driver = new ChromeDriver(options);
        try {
            driver.get(url);
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
