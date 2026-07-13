package com.mattoid.scheduled.service.wecom;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证应用管理页 URL 自动发现的核心逻辑：
 * 1. Playwright 网络拦截（page.onResponse）能捕获 AJAX 响应
 * 2. 正则能从 JSON 中提取 modApiApp ID 和 agentId
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WeComIpSyncDiscoveryTest {

    private static Playwright playwright;
    private static Browser browser;

    @BeforeAll
    static void setup() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(true)
                .setArgs(List.of("--no-sandbox", "--disable-dev-shm-usage")));
    }

    @AfterAll
    static void teardown() {
        if (browser != null) browser.close();
        if (playwright != null) playwright.close();
    }

    @Test
    @Order(1)
    void testNetworkInterceptionCapturesResponses() {
        BrowserContext context = browser.newContext();
        Page page = context.newPage();

        List<String> capturedUrls = Collections.synchronizedList(new ArrayList<>());
        List<String> capturedJsonBodies = Collections.synchronizedList(new ArrayList<>());

        page.onResponse(response -> {
            try {
                if (response.status() == 200) {
                    String ct = response.headers().get("content-type");
                    if (ct != null && ct.contains("json")) {
                        String body = response.text();
                        if (body != null && body.length() > 10) {
                            capturedUrls.add(response.url());
                            capturedJsonBodies.add(body);
                        }
                    }
                }
            } catch (Exception ignored) {}
        });

        // Navigate to a page that returns JSON (jsonplaceholder is more reliable than httpbin)
        page.navigate("https://jsonplaceholder.typicode.com/posts/1", new Page.NavigateOptions().setTimeout(15000));
        page.waitForTimeout(2000);

        assertFalse(capturedJsonBodies.isEmpty(), "应该至少捕获到一个 JSON 响应");
        System.out.println("捕获到 " + capturedJsonBodies.size() + " 个 JSON 响应");
        System.out.println("URLs: " + capturedUrls);

        context.close();
    }

    @Test
    @Order(2)
    void testModApiAppIdExtraction() {
        // 模拟企业微信后台 AJAX 返回的 JSON 数据
        String sampleJson = """
                {
                  "errcode": 0,
                  "applist": [
                    {"appid": 1000017, "name": "测试应用", "modApiApp/5629502132772163": true},
                    {"agentid": 1000018, "name": "另一个应用"}
                  ],
                  "url": "https://work.weixin.qq.com/wework_admin/frame#apps/modApiApp/5629502132772163"
                }
                """;

        Pattern modApiAppPattern = Pattern.compile("modApiApp/(\\d+)");
        Pattern agentIdPattern = Pattern.compile("agent[_]?[iI]d[\"']?\\s*[:=]\\s*[\"']?(\\d+)", Pattern.CASE_INSENSITIVE);

        // 提取 modApiApp IDs
        LinkedHashMap<String, String> appIdToAgentId = new LinkedHashMap<>();
        Matcher m = modApiAppPattern.matcher(sampleJson);
        while (m.find()) {
            String modApiAppId = m.group(1);
            Matcher am = agentIdPattern.matcher(sampleJson);
            String agentId = am.find() ? am.group(1) : null;
            appIdToAgentId.put(modApiAppId, agentId);
        }

        assertEquals(1, appIdToAgentId.size(), "应该提取到 1 个 modApiApp ID");
        assertTrue(appIdToAgentId.containsKey("5629502132772163"), "应该包含 modApiApp ID 5629502132772163");
        System.out.println("提取结果: " + appIdToAgentId);
    }

    @Test
    @Order(3)
    void testAgentIdExtraction() {
        // 测试各种 agentId 格式
        String[] testCases = {
                "{\"agentid\":1000017}",
                "{\"agentid\":\"1000018\"}",
                "{\"agent_id\":1000019}",
                "agentid=1000020",
                "\"AgentId\": \"1000021\"",
        };

        Pattern agentIdPattern = Pattern.compile("agent[_]?[iI]d[\"']?\\s*[:=]\\s*[\"']?(\\d+)", Pattern.CASE_INSENSITIVE);

        for (String json : testCases) {
            Matcher m = agentIdPattern.matcher(json);
            if (m.find()) {
                System.out.println("输入: " + json + " -> agentId: " + m.group(1));
            } else {
                System.out.println("输入: " + json + " -> 未匹配");
            }
        }
        // agentid 格式应匹配
        Matcher m1 = agentIdPattern.matcher("{\"agentid\":1000017}");
        assertTrue(m1.find(), "应该匹配 agentid");
        assertEquals("1000017", m1.group(1));
    }

    @Test
    @Order(4)
    void testAgentIdPagePatternMatching() {
        // 模拟应用详情页中包含 AgentId 的 HTML
        String sampleHtml = """
                <div class="app_detail">
                  <span>AgentId: 1000017</span>
                </div>
                """;

        String targetAgentId = "1000017";
        Pattern pageAgentIdPattern = Pattern.compile("AgentId\\D{0,10}" + targetAgentId + "\\b");

        Matcher m = pageAgentIdPattern.matcher(sampleHtml);
        assertTrue(m.find(), "应该在 HTML 中找到 AgentId=1000017");
        System.out.println("页面 AgentId 匹配成功");
    }

    @Test
    @Order(5)
    void testNetworkInterceptionWithJsonApi() {
        BrowserContext context = browser.newContext();
        Page page = context.newPage();

        List<String> jsonBodies = Collections.synchronizedList(new ArrayList<>());
        page.onResponse(response -> {
            try {
                if (response.status() == 200) {
                    String ct = response.headers().get("content-type");
                    if (ct != null && (ct.contains("json") || ct.contains("text/plain"))) {
                        String body = response.text();
                        if (body != null && body.length() > 50) {
                            jsonBodies.add(body);
                        }
                    }
                }
            } catch (Exception ignored) {}
        });

        // 使用 JSONPlaceholder API 测试
        page.navigate("https://jsonplaceholder.typicode.com/users", new Page.NavigateOptions().setTimeout(15000));
        try {
            page.waitForLoadState(com.microsoft.playwright.options.LoadState.NETWORKIDLE,
                    new Page.WaitForLoadStateOptions().setTimeout(10000));
        } catch (Exception ignored) {}

        assertFalse(jsonBodies.isEmpty(), "应该捕获到 JSONPlaceholder 的用户列表 JSON");

        // 验证能从中提取 id 字段
        String body = jsonBodies.get(0);
        Pattern idPattern = Pattern.compile("\"id\"\\s*:\\s*(\\d+)");
        Matcher m = idPattern.matcher(body);
        assertTrue(m.find(), "应该能从 JSON 中提取 id 字段");
        System.out.println("从 JSONPlaceholder 提取到第一个 id: " + m.group(1));
        System.out.println("捕获到 " + jsonBodies.size() + " 个 JSON 响应，第一个长度: " + body.length());

        context.close();
    }

    @Test
    @Order(6)
    void testWeComFramePageLoads() {
        BrowserContext context = browser.newContext();
        Page page = context.newPage();

        // 未登录状态访问企业微信后台，应该重定向到登录页
        page.navigate("https://work.weixin.qq.com/wework_admin/frame#/apps",
                new Page.NavigateOptions().setTimeout(30000));
        page.waitForTimeout(3000);

        String url = page.url();
        String title = page.title();
        String content = page.content();

        System.out.println("URL: " + url);
        System.out.println("Title: " + title);
        System.out.println("Content length: " + (content != null ? content.length() : 0));

        // 未登录时应该被重定向到登录页
        boolean isLoginRelated = (url != null && (url.contains("login") || url.contains("Login")))
                || (title != null && (title.contains("登录") || title.contains("login")))
                || (content != null && (content.contains("loginpage") || content.contains("qrcode_login")));

        assertTrue(isLoginRelated, "未登录状态应该被重定向到登录页或显示登录内容");
        System.out.println("未登录重定向验证通过");

        context.close();
    }
}
