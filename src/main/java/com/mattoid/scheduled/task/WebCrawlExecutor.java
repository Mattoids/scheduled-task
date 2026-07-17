package com.mattoid.scheduled.task;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.mattoid.scheduled.datasource.SshConfig;
import com.mattoid.scheduled.datasource.SshHopConfig;
import com.mattoid.scheduled.datasource.SshTunnel;
import com.mattoid.scheduled.datasource.SshTunnelManager;
import com.mattoid.scheduled.entity.TaskWebCrawlConfig;
import com.mattoid.scheduled.entity.TaskWebCrawlSelector;
import com.mattoid.scheduled.util.CryptoUtil;
import com.mattoid.scheduled.util.PlaceholderUtils;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

@Slf4j
@Component
public class WebCrawlExecutor {

    private static final String ENC_PREFIX = "ENC(";
    private static final int DEFAULT_TIMEOUT_MS = 30_000;

    private static volatile SSLSocketFactory trustAllSslSocketFactory;

    private final ObjectMapper objectMapper;
    private final WebCrawlMediaDownloader mediaDownloader;
    private final WebDriverManager webDriverManager;
    private final SshTunnelManager sshTunnelManager;
    private final ThreadLocal<JsonNode> currentJsonResponse = new ThreadLocal<>();

    public WebCrawlExecutor(ObjectMapper objectMapper,
                            WebCrawlMediaDownloader mediaDownloader,
                            WebDriverManager webDriverManager,
                            SshTunnelManager sshTunnelManager) {
        this.objectMapper = objectMapper;
        this.mediaDownloader = mediaDownloader;
        this.webDriverManager = webDriverManager;
        this.sshTunnelManager = sshTunnelManager;
    }

    public WebCrawlResult execute(TaskWebCrawlConfig config, Map<String, Object> params) throws Exception {
        if (config == null) {
            throw new IllegalArgumentException("爬取配置不能为空");
        }
        decryptSensitiveFields(config);
        Map<String, Object> mergedParams = mergeParams(config, params);

        String tunnelId = null;
        SshTunnel tunnel = null;
        WebCrawlProxyHelper.bindAuth(config);
        try {
            if (Integer.valueOf(1).equals(config.getSshEnabled())) {
                SshConfig sshConfig = buildSshConfig(config);
                tunnelId = "crawl_" + config.getId() + "_" + System.currentTimeMillis();
                tunnel = sshTunnelManager.createTunnel(sshConfig, tunnelId);
            }

            List<Map<String, Object>> allData = new ArrayList<>();
            List<File> mediaFiles = new ArrayList<>();
            int mediaCount = 0;

            String currentUrl = replaceRequestVariables(config.getRequestUrl(), mergedParams);
            int maxPages = config.getPaginationMaxPages() != null ? config.getPaginationMaxPages() : 1;
            if (Integer.valueOf(1).equals(config.getPaginationEnabled()) && maxPages <= 0) {
                maxPages = Integer.MAX_VALUE;
            }
            if (!Integer.valueOf(1).equals(config.getPaginationEnabled())) {
                maxPages = 1;
            }

            for (int page = 1; page <= maxPages; page++) {
                log.info("开始爬取第 {} 页: {}", page, currentUrl);
                Document document = fetchDocument(config, currentUrl, mergedParams, tunnel);
                List<Map<String, Object>> pageData = extractData(config, document, currentUrl);
                allData.addAll(pageData);

                if (Integer.valueOf(1).equals(config.getMediaEnabled())) {
                    WebCrawlMediaDownloader.MediaResult result = mediaDownloader.download(document, config, mergedParams, tunnel);
                    mediaFiles.addAll(result.files());
                    mediaCount += result.count();
                }

                if (page >= maxPages) {
                    break;
                }
                String nextUrl = resolveNextUrl(config, document, currentUrl, page + 1, mergedParams);
                if (!StringUtils.hasText(nextUrl)) {
                    log.info("未发现下一页，结束分页爬取");
                    break;
                }
                currentUrl = nextUrl;
            }

            return new WebCrawlResult(
                    config.getCrawlName(),
                    config.getCrawlCode(),
                    allData,
                    mediaFiles,
                    allData.size(),
                    mediaCount
            );
        } finally {
            clearJsonResponse();
            WebCrawlProxyHelper.unbindAuth();
            if (tunnelId != null) {
                sshTunnelManager.closeTunnel(tunnelId);
            }
        }
    }

    public WebCrawlPreviewResult preview(TaskWebCrawlConfig config, Map<String, Object> params) {
        if (config == null) {
            return WebCrawlPreviewResult.failure("爬取配置不能为空");
        }
        if (!StringUtils.hasText(config.getRequestUrl())) {
            return WebCrawlPreviewResult.failure("请求 URL 不能为空");
        }
        decryptSensitiveFields(config);
        Map<String, Object> mergedParams = mergeParams(config, params);

        String tunnelId = null;
        SshTunnel tunnel = null;
        WebCrawlProxyHelper.bindAuth(config);
        try {
            if (Integer.valueOf(1).equals(config.getSshEnabled())) {
                SshConfig sshConfig = buildSshConfig(config);
                tunnelId = "crawl_preview_" + System.currentTimeMillis();
                tunnel = sshTunnelManager.createTunnel(sshConfig, tunnelId);
            }

            String url = replaceRequestVariables(config.getRequestUrl(), mergedParams);
            String actualUrl = applySshTunnelToUrl(url, tunnel);
            if (tunnel != null) {
                log.debug("SSH 隧道预览请求: originalUrl={}, actualUrl={}", url, actualUrl);
            }

            PreviewFetchResult fetchResult = fetchPreviewDocument(config, actualUrl, mergedParams, tunnel);
            Document document = fetchResult.document();
            int statusCode = fetchResult.statusCode();
            String title = document.title();
            log.info("网页爬取预览结果: statusCode={}, title={}, url={}", statusCode, title, actualUrl);
            String previewContent = buildPreviewContent(config, document);
            return WebCrawlPreviewResult.success(statusCode, fetchResult.message(), title, previewContent);
        } catch (Exception e) {
            log.warn("网页爬取预览失败: {}", e.getMessage(), e);
            return WebCrawlPreviewResult.failure("预览失败: " + e.getMessage());
        } finally {
            clearJsonResponse();
            WebCrawlProxyHelper.unbindAuth();
            if (tunnelId != null) {
                sshTunnelManager.closeTunnel(tunnelId);
            }
        }
    }

    public WebCrawlPreviewJsonResult previewJson(TaskWebCrawlConfig config, Map<String, Object> params) {
        if (config == null) {
            return WebCrawlPreviewJsonResult.failure("爬取配置不能为空");
        }
        if (!StringUtils.hasText(config.getRequestUrl())) {
            return WebCrawlPreviewJsonResult.failure("请求 URL 不能为空");
        }
        decryptSensitiveFields(config);
        Map<String, Object> mergedParams = mergeParams(config, params);

        String tunnelId = null;
        SshTunnel tunnel = null;
        WebCrawlProxyHelper.bindAuth(config);
        try {
            if (Integer.valueOf(1).equals(config.getSshEnabled())) {
                SshConfig sshConfig = buildSshConfig(config);
                tunnelId = "crawl_preview_json_" + System.currentTimeMillis();
                tunnel = sshTunnelManager.createTunnel(sshConfig, tunnelId);
            }

            String url = replaceRequestVariables(config.getRequestUrl(), mergedParams);
            String actualUrl = applySshTunnelToUrl(url, tunnel);
            if (tunnel != null) {
                log.debug("SSH 隧道 JSON 预览请求: originalUrl={}, actualUrl={}", url, actualUrl);
            }

            PreviewFetchResult fetchResult = fetchPreviewDocument(config, actualUrl, mergedParams, tunnel);
            Document document = fetchResult.document();
            int statusCode = fetchResult.statusCode();
            String title = document.title();
            Object data = buildPreviewJsonData(config, document, document.baseUri());
            log.info("网页 JSON 预览结果: statusCode={}, title={}, url={}, dataType={}",
                    statusCode, title, actualUrl,
                    data instanceof List ? "selectors" : "generic");
            return WebCrawlPreviewJsonResult.success(statusCode, fetchResult.message(), title, data);
        } catch (Exception e) {
            log.warn("网页 JSON 预览失败: {}", e.getMessage(), e);
            return WebCrawlPreviewJsonResult.failure("预览失败: " + e.getMessage());
        } finally {
            clearJsonResponse();
            WebCrawlProxyHelper.unbindAuth();
            if (tunnelId != null) {
                sshTunnelManager.closeTunnel(tunnelId);
            }
        }
    }

    public ResourceResponse fetchResource(TaskWebCrawlConfig config, String targetUrl) throws Exception {
        if (config == null || !StringUtils.hasText(targetUrl)) {
            throw new IllegalArgumentException("配置或资源 URL 不能为空");
        }
        decryptSensitiveFields(config);
        Map<String, Object> mergedParams = mergeParams(config, null);

        String tunnelId = null;
        SshTunnel tunnel = null;
        WebCrawlProxyHelper.bindAuth(config);
        try {
            if (Integer.valueOf(1).equals(config.getSshEnabled())) {
                SshConfig sshConfig = buildSshConfig(config);
                tunnelId = "crawl_resource_" + System.currentTimeMillis();
                tunnel = sshTunnelManager.createTunnel(sshConfig, tunnelId);
            }

            String actualUrl = applySshTunnelToUrl(targetUrl, tunnel);
            Connection connection = buildPreviewConnection(config, actualUrl, mergedParams, tunnel);
            Connection.Response response = connection.execute();
            return new ResourceResponse(response.contentType(), response.bodyAsBytes());
        } finally {
            clearJsonResponse();
            WebCrawlProxyHelper.unbindAuth();
            if (tunnelId != null) {
                sshTunnelManager.closeTunnel(tunnelId);
            }
        }
    }

    public record ResourceResponse(String contentType, byte[] body) {
    }

    public record PreviewFetchResult(Document document, int statusCode, String message) {
    }

    private PreviewFetchResult fetchPreviewDocument(TaskWebCrawlConfig config, String actualUrl,
                                                    Map<String, Object> params, SshTunnel tunnel) throws Exception {
        if ("DYNAMIC".equalsIgnoreCase(config.getRenderType())) {
            Document document = webDriverManager.fetchPage(config, actualUrl, params);
            tryParseJsonFromDocument(document);
            return new PreviewFetchResult(document, 200, "页面可访问");
        }
        Connection connection = buildPreviewConnection(config, actualUrl, params, tunnel);
        Connection.Response response = connection.execute();
        String body = safeResponseBody(response);
        Document document = response.parse();
        if (!StringUtils.hasText(body)) {
            body = document.text();
        }
        tryParseJsonResponse(body, response.contentType());
        int statusCode = response.statusCode();
        String message = (statusCode >= 200 && statusCode < 400)
                ? "页面可访问" : "请求返回非成功状态码: " + statusCode;
        return new PreviewFetchResult(document, statusCode, message);
    }

    private void tryParseJsonResponse(String body, String contentType) {
        if (!StringUtils.hasText(body)) {
            clearJsonResponse();
            return;
        }
        String trimmed = body.trim();
        String lowerCt = contentType != null ? contentType.toLowerCase() : "";
        if (!lowerCt.contains("json") && !trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            clearJsonResponse();
            return;
        }
        try {
            currentJsonResponse.set(objectMapper.readTree(body));
        } catch (Exception e) {
            log.debug("响应内容不是 JSON: {}", e.getMessage());
            clearJsonResponse();
        }
    }

    private void tryParseJsonFromDocument(Document document) {
        if (document == null) {
            clearJsonResponse();
            return;
        }
        String text = document.text();
        tryParseJsonResponse(text, null);
    }

    private void clearJsonResponse() {
        currentJsonResponse.remove();
    }

    private static SSLSocketFactory getTrustAllSslSocketFactory() throws Exception {
        if (trustAllSslSocketFactory == null) {
            synchronized (WebCrawlExecutor.class) {
                if (trustAllSslSocketFactory == null) {
                    TrustManager[] trustAllCerts = new TrustManager[]{
                            new X509TrustManager() {
                                public X509Certificate[] getAcceptedIssuers() {
                                    return new X509Certificate[0];
                                }

                                public void checkClientTrusted(X509Certificate[] certs, String authType) {
                                }

                                public void checkServerTrusted(X509Certificate[] certs, String authType) {
                                }
                            }
                    };
                    SSLContext sc = SSLContext.getInstance("TLS");
                    sc.init(null, trustAllCerts, new java.security.SecureRandom());
                    trustAllSslSocketFactory = sc.getSocketFactory();
                }
            }
        }
        return trustAllSslSocketFactory;
    }

    private static HostnameVerifier createExpectedHostVerifier(String expectedHost) {
        if (!StringUtils.hasText(expectedHost)) {
            return (hostname, session) -> true;
        }
        HostnameVerifier defaultVerifier = HttpsURLConnection.getDefaultHostnameVerifier();
        return (hostname, session) -> defaultVerifier.verify(expectedHost, session);
    }

    private static void applyTunnelSslSettings(Connection connection, String url, SshTunnel tunnel,
                                                String expectedHost) {
        if (tunnel == null || url == null || !url.toLowerCase().startsWith("https")) {
            return;
        }
        try {
            connection.sslSocketFactory(getTrustAllSslSocketFactory());
        } catch (Exception e) {
            log.warn("设置 SSH 隧道 HTTPS 绕过失败: {}", e.getMessage());
        }
    }

    private String resolveExpectedRemoteHost(TaskWebCrawlConfig config) {
        String remoteHost = config.getSshRemoteHost();
        if (StringUtils.hasText(remoteHost)) {
            return remoteHost;
        }
        URL parsedUrl = parseRequestUrl(config.getRequestUrl());
        if (parsedUrl != null) {
            return parsedUrl.getHost();
        }
        return null;
    }

    private String safeResponseBody(Connection.Response response) {
        if (response == null) {
            return null;
        }
        try {
            return response.body();
        } catch (Exception e) {
            log.debug("读取响应体失败: {}", e.getMessage());
            return null;
        }
    }

    private String buildPreviewContent(TaskWebCrawlConfig config, Document document) {
        List<TaskWebCrawlSelector> selectors = config.getSelectors();
        if (isSelectorPreviewEnabled(config)
                && !CollectionUtils.isEmpty(selectors)
                && selectors.stream().anyMatch(this::isEffectiveSelector)) {
            List<Map<String, Object>> data = extractData(config, document, document.baseUri());
            try {
                String json = objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(data);
                log.debug("预览已应用选择器，提取数据行数={}", data.size());
                return "<pre>" + json + "</pre>";
            } catch (Exception e) {
                log.warn("预览选择器结果序列化失败: {}", e.getMessage());
                return "<pre>" + data + "</pre>";
            }
        }
        log.debug("预览未启用选择器或无有效选择器，返回完整页面 HTML");
        return document.html();
    }

    private boolean isSelectorPreviewEnabled(TaskWebCrawlConfig config) {
        return config.getPreviewSelectorEnabled() == null
                || Integer.valueOf(1).equals(config.getPreviewSelectorEnabled());
    }

    private boolean isEffectiveSelector(TaskWebCrawlSelector selector) {
        return selector != null
                && (StringUtils.hasText(selector.getSelectorType())
                || StringUtils.hasText(selector.getSelectorValue())
                || StringUtils.hasText(selector.getFieldName()));
    }

    private boolean isMeaningfulData(List<Map<String, Object>> data) {
        if (CollectionUtils.isEmpty(data)) {
            return false;
        }
        for (Map<String, Object> row : data) {
            if (row == null) {
                continue;
            }
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if (StringUtils.hasText(entry.getKey()) && entry.getValue() != null) {
                    return true;
                }
            }
        }
        return false;
    }

    private Object buildPreviewJsonData(TaskWebCrawlConfig config, Document document, String baseUrl) {
        List<TaskWebCrawlSelector> selectors = config.getSelectors();
        if (isSelectorPreviewEnabled(config)
                && !CollectionUtils.isEmpty(selectors)
                && selectors.stream().anyMatch(this::isEffectiveSelector)) {
            List<Map<String, Object>> data = extractData(config, document, baseUrl);
            log.debug("JSON 预览已应用选择器，提取数据行数={}", data.size());
            return data;
        }
        log.debug("JSON 预览未启用选择器或无有效选择器，返回通用网页 JSON");
        return parseGenericJson(document, baseUrl);
    }

    private Map<String, Object> parseGenericJson(Document document, String baseUrl) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("title", document.title());
        result.put("url", baseUrl);
        result.put("description", metaContent(document, "description"));
        result.put("keywords", metaContent(document, "keywords"));
        result.put("headings", extractHeadings(document));
        result.put("links", extractLinks(document, baseUrl));
        result.put("images", extractImages(document, baseUrl));
        result.put("videos", extractMedia(document, baseUrl, "video"));
        result.put("audios", extractMedia(document, baseUrl, "audio"));
        result.put("paragraphs", extractParagraphs(document));
        return result;
    }

    private String metaContent(Document document, String name) {
        Element element = document.selectFirst("meta[name=" + name + "]");
        if (element == null) {
            element = document.selectFirst("meta[property=" + name + "]");
        }
        return element != null ? element.attr("content") : null;
    }

    private List<Map<String, Object>> extractHeadings(Document document) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Element element : document.select("h1,h2,h3,h4,h5,h6")) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("level", element.tagName());
            item.put("text", element.text().trim());
            list.add(item);
        }
        return list;
    }

    private List<Map<String, Object>> extractLinks(Document document, String baseUrl) {
        List<Map<String, Object>> list = new ArrayList<>();
        int limit = 200;
        for (Element element : document.select("a[href]")) {
            if (list.size() >= limit) {
                break;
            }
            String href = element.absUrl("href");
            if (!StringUtils.hasText(href)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("text", element.text().trim());
            item.put("href", href);
            item.put("title", element.attr("title"));
            list.add(item);
        }
        return list;
    }

    private List<Map<String, Object>> extractImages(Document document, String baseUrl) {
        List<Map<String, Object>> list = new ArrayList<>();
        int limit = 200;
        for (Element element : document.select("img")) {
            if (list.size() >= limit) {
                break;
            }
            String src = element.absUrl("src");
            if (!StringUtils.hasText(src)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("src", src);
            item.put("alt", element.attr("alt"));
            item.put("width", element.attr("width"));
            item.put("height", element.attr("height"));
            list.add(item);
        }
        return list;
    }

    private List<Map<String, Object>> extractMedia(Document document, String baseUrl, String tag) {
        List<Map<String, Object>> list = new ArrayList<>();
        int limit = 100;
        for (Element element : document.select(tag)) {
            if (list.size() >= limit) {
                break;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            String src = element.absUrl("src");
            if (!StringUtils.hasText(src)) {
                Element source = element.selectFirst("source");
                src = source != null ? source.absUrl("src") : null;
            }
            item.put("src", src);
            item.put("poster", "video".equals(tag) ? element.absUrl("poster") : null);
            list.add(item);
        }
        return list;
    }

    private List<String> extractParagraphs(Document document) {
        List<String> list = new ArrayList<>();
        int limit = 100;
        for (Element element : document.select("p")) {
            if (list.size() >= limit) {
                break;
            }
            String text = element.text().trim();
            if (StringUtils.hasText(text)) {
                list.add(text);
            }
        }
        return list;
    }

    private Document fetchDocument(TaskWebCrawlConfig config, String url,
                                   Map<String, Object> params, SshTunnel tunnel) throws Exception {
        String actualUrl = applySshTunnelToUrl(url, tunnel);
        if ("DYNAMIC".equalsIgnoreCase(config.getRenderType())) {
            Document document = webDriverManager.fetchPage(config, actualUrl, params);
            tryParseJsonFromDocument(document);
            return document;
        }
        Connection connection = buildConnection(config, actualUrl, params, tunnel);
        Connection.Response response = connection.execute();
        int statusCode = response.statusCode();
        if (statusCode < 200 || statusCode >= 400) {
            log.warn("爬取请求返回非成功状态码: statusCode={}, url={}", statusCode, actualUrl);
        }
        String body = safeResponseBody(response);
        Document document = response.parse();
        if (!StringUtils.hasText(body)) {
            body = document.text();
        }
        tryParseJsonResponse(body, response.contentType());
        if (tunnel != null) {
            document.setBaseUri(url);
        }
        return document;
    }

    private Connection buildConnection(TaskWebCrawlConfig config, String url,
                                       Map<String, Object> params, SshTunnel tunnel) throws IOException {
        Connection.Method method = parseMethod(config.getRequestMethod());
        String effectiveUrl = url;
        Connection connection = Jsoup.connect(url)
                .method(method)
                .timeout(DEFAULT_TIMEOUT_MS)
                .followRedirects(true)
                .ignoreHttpErrors(true)
                .ignoreContentType(true);

        applyTunnelSslSettings(connection, url, tunnel, resolveExpectedRemoteHost(config));

        Proxy proxy = WebCrawlProxyHelper.createProxy(config);
        if (proxy != null) {
            connection.proxy(proxy);
        }

        applyHeaders(connection, config.getRequestHeaders(), params);
        applyCookies(connection, config.getCookies(), params);
        applyAuth(connection, config);

        Map<String, String> requestParams = parseJsonMap(replaceRequestVariables(config.getRequestParams(), params));
        if (method == Connection.Method.POST || method == Connection.Method.PUT) {
            String body = replaceRequestVariables(config.getRequestBody(), params);
            if (StringUtils.hasText(body)) {
                // 有 requestBody 时，URL 参数拼到地址栏，避免和 body 冲突
                if (!requestParams.isEmpty()) {
                    effectiveUrl = appendQueryParams(url, requestParams);
                    connection.url(effectiveUrl);
                }
                if (StringUtils.hasText(config.getRequestContentType())) {
                    connection.requestBody(body);
                    connection.header("Content-Type", config.getRequestContentType());
                } else {
                    connection.requestBody(body);
                }
            } else {
                if (!requestParams.isEmpty()) {
                    connection.data(requestParams);
                }
            }
        } else {
            if (!requestParams.isEmpty()) {
                connection.data(requestParams);
            }
        }
        return connection;
    }

    private Connection buildPreviewConnection(TaskWebCrawlConfig config, String url,
                                              Map<String, Object> params, SshTunnel tunnel) throws IOException {
        Connection.Method method = parseMethod(config.getRequestMethod());
        String effectiveUrl = url;
        Connection connection = Jsoup.connect(url)
                .method(method)
                .timeout(DEFAULT_TIMEOUT_MS)
                .followRedirects(true)
                .ignoreHttpErrors(true)
                .ignoreContentType(true);

        applyTunnelSslSettings(connection, url, tunnel, resolveExpectedRemoteHost(config));

        Proxy proxy = WebCrawlProxyHelper.createProxy(config);
        if (proxy != null) {
            connection.proxy(proxy);
        }

        applyHeaders(connection, config.getRequestHeaders(), params);
        applyCookies(connection, config.getCookies(), params);
        applyAuth(connection, config);

        Map<String, String> requestParams = parseJsonMap(replaceRequestVariables(config.getRequestParams(), params));
        if (method == Connection.Method.POST || method == Connection.Method.PUT) {
            String body = replaceRequestVariables(config.getRequestBody(), params);
            if (StringUtils.hasText(body)) {
                if (!requestParams.isEmpty()) {
                    effectiveUrl = appendQueryParams(url, requestParams);
                    connection.url(effectiveUrl);
                }
                if (StringUtils.hasText(config.getRequestContentType())) {
                    connection.requestBody(body);
                    connection.header("Content-Type", config.getRequestContentType());
                } else {
                    connection.requestBody(body);
                }
            } else {
                if (!requestParams.isEmpty()) {
                    connection.data(requestParams);
                }
            }
        } else {
            if (!requestParams.isEmpty()) {
                connection.data(requestParams);
            }
        }
        return connection;
    }

    private void applyHeaders(Connection connection, String headersJson, Map<String, Object> params) {
        Map<String, String> headers = parseJsonMap(replaceRequestVariables(headersJson, params));
        headers.forEach(connection::header);
        if (!headers.containsKey("User-Agent")) {
            connection.userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36");
        }
    }

    private void applyCookies(Connection connection, String cookiesString, Map<String, Object> params) {
        Map<String, String> cookies = parseCookieMap(replaceRequestVariables(cookiesString, params));
        cookies.forEach(connection::cookie);
    }

    private Map<String, String> parseCookieMap(String value) {
        Map<String, String> cookies = new LinkedHashMap<>();
        if (!StringUtils.hasText(value)) {
            return cookies;
        }
        // 优先尝试 JSON 格式
        try {
            Map<String, Object> map = objectMapper.readValue(value, new TypeReference<Map<String, Object>>() {
            });
            map.forEach((k, v) -> cookies.put(k, v != null ? String.valueOf(v) : ""));
            return cookies;
        } catch (Exception ignored) {
        }
        // 回退到标准 HTTP Cookie 格式：name=value; name=value
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

    private void applyAuth(Connection connection, TaskWebCrawlConfig config) {
        String authType = config.getAuthType();
        if (!StringUtils.hasText(authType) || "NONE".equalsIgnoreCase(authType)) {
            return;
        }
        switch (authType.toUpperCase()) {
            case "BASIC" -> applyBasicAuth(connection, config.getAuthConfig());
            case "TOKEN" -> applyTokenAuth(connection, config.getAuthConfig());
            case "FORM" -> {
                // FORM 认证需要先登录获取 cookie，这里仅设置已有 cookie 即可
                // 如需先登录，可在 customParams 中设置 loginUrl/loginBody 由外部预处理
                log.warn("FORM 认证建议先通过预处理获取 Cookie 后配置到 cookies 字段");
            }
            case "OAUTH2" -> applyOAuth2Auth(connection, config.getAuthConfig());
            default -> log.warn("不支持的认证类型: {}", authType);
        }
    }

    private void applyBasicAuth(Connection connection, String authConfig) {
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
                connection.header("Authorization", "Basic " + credentials);
            }
            return;
        }
        // 非 JSON：直接视为 Base64 凭证或完整 Authorization 头值
        String value = authConfig.trim();
        if (value.toUpperCase().startsWith("BASIC ")) {
            connection.header("Authorization", value);
        } else {
            connection.header("Authorization", "Basic " + value);
        }
    }

    private void applyTokenAuth(Connection connection, String authConfig) {
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
            connection.header(headerName, (prefix + " " + token).trim());
        }
    }

    private void applyOAuth2Auth(Connection connection, String authConfig) {
        Map<String, String> auth = parseJsonMap(authConfig, false);
        String token = auth.get("accessToken");
        if (StringUtils.hasText(token)) {
            connection.header("Authorization", "Bearer " + token);
        }
    }

    private List<Map<String, Object>> extractData(TaskWebCrawlConfig config, Document document, String baseUrl) {
        List<TaskWebCrawlSelector> selectors = config.getSelectors();
        if (CollectionUtils.isEmpty(selectors)) {
            return Collections.emptyList();
        }
        selectors = selectors.stream()
                .filter(this::isEffectiveSelector)
                .collect(Collectors.toList());
        if (selectors.isEmpty()) {
            return Collections.emptyList();
        }

        // 如果选择器包含 JSON 类型，但当前响应不是 JSON（例如 HTML 页面），
        // 先把整页转成通用 JSON，再让 JSONPath 选择器基于该 JSON 提取
        ensureJsonResponseFromHtml(selectors, document, baseUrl);

        // 找到第一个选择器匹配的元素集合，作为行基础
        TaskWebCrawlSelector rowSelector = findRowSelector(selectors);
        Elements rows;
        if (rowSelector != null && "CSS".equalsIgnoreCase(rowSelector.getSelectorType())) {
            rows = document.select(rowSelector.getSelectorValue());
        } else {
            rows = new Elements(document);
        }
        log.debug("选择器提取: crawlCode={}, rowSelector={}, 匹配行数={}",
                config.getCrawlCode(),
                rowSelector != null ? rowSelector.getSelectorValue() : "无",
                rows.size());

        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            Element context = rows.get(i);
            Map<String, Object> row = new LinkedHashMap<>();
            for (TaskWebCrawlSelector selector : selectors) {
                if (isRowSelector(selector)) {
                    continue;
                }
                Object value = extractValue(selector, context, document, baseUrl);
                row.put(selector.getFieldName(), value);
            }
            if (!row.isEmpty()) {
                data.add(row);
            }
        }
        if (data.isEmpty()) {
            // 没有行级选择器时，整页作为一行
            Map<String, Object> row = new LinkedHashMap<>();
            for (TaskWebCrawlSelector selector : selectors) {
                Object value = extractValue(selector, document, document, baseUrl);
                row.put(selector.getFieldName(), value);
            }
            if (!row.isEmpty()) {
                data.add(row);
            }
        }
        if (log.isDebugEnabled()) {
            for (int i = 0; i < data.size(); i++) {
                log.debug("选择器提取 第{}行: {}", i, data.get(i));
            }
        }
        return data;
    }

    private void ensureJsonResponseFromHtml(List<TaskWebCrawlSelector> selectors, Document document, String baseUrl) {
        if (currentJsonResponse.get() != null) {
            return;
        }
        boolean hasJsonSelector = selectors.stream()
                .anyMatch(s -> "JSON".equalsIgnoreCase(s.getSelectorType())
                        && StringUtils.hasText(s.getSelectorValue()));
        if (!hasJsonSelector) {
            return;
        }
        try {
            Map<String, Object> genericJson = parseGenericJson(document, baseUrl);
            currentJsonResponse.set(objectMapper.valueToTree(genericJson));
            log.debug("HTML 页面已转换为通用 JSON 供 JSON 选择器使用, baseUrl={}", baseUrl);
        } catch (Exception e) {
            log.warn("HTML 页面转换为通用 JSON 失败: {}", e.getMessage());
        }
    }

    private TaskWebCrawlSelector findRowSelector(List<TaskWebCrawlSelector> selectors) {
        return selectors.stream()
                .filter(this::isRowSelector)
                .findFirst()
                .orElse(null);
    }

    private boolean isRowSelector(TaskWebCrawlSelector selector) {
        return Integer.valueOf(1).equals(selector.getIsRowSelector());
    }

    private Object extractValue(TaskWebCrawlSelector selector, Element context, Document document, String baseUrl) {
        String selectorType = selector.getSelectorType();
        String selectorValue = selector.getSelectorValue();
        if (!StringUtils.hasText(selectorType) || !StringUtils.hasText(selectorValue)) {
            return selector.getDefaultValue();
        }

        Object rawValue;
        try {
            rawValue = switch (selectorType.toUpperCase()) {
                case "CSS" -> extractCssValue(selector, context, baseUrl);
                case "XPATH" -> extractXPathValue(selector, document);
                case "REGEX" -> extractRegexValue(selector, context.html());
                case "JSON" -> extractJsonValue(selector);
                case "FUZZY" -> extractFuzzyValue(selector, context, baseUrl);
                default -> null;
            };
        } catch (Exception e) {
            log.warn("选择器提取失败 [{}]: {}", selector.getFieldName(), e.getMessage());
            rawValue = null;
        }
        if (rawValue == null || (rawValue instanceof String s && !StringUtils.hasText(s))) {
            return selector.getDefaultValue();
        }
        if (rawValue instanceof String s) {
            return convertDataType(s, selector.getDataType());
        }
        return rawValue;
    }

    private String extractCssValue(TaskWebCrawlSelector selector, Element context, String baseUrl) {
        Elements elements = context.select(selector.getSelectorValue());
        if (elements.isEmpty()) {
            return null;
        }
        return extractElementAttribute(selector, elements.first(), baseUrl);
    }

    private String extractElementAttribute(TaskWebCrawlSelector selector, Element target, String baseUrl) {
        if (target == null) {
            return null;
        }
        String attribute = selector.getAttribute();
        String value;
        if (!StringUtils.hasText(attribute) || "text".equalsIgnoreCase(attribute)) {
            value = target.text();
        } else if ("html".equalsIgnoreCase(attribute)) {
            value = target.html();
        } else if ("src".equalsIgnoreCase(attribute) || "href".equalsIgnoreCase(attribute)) {
            value = target.absUrl(attribute);
        } else if (attribute.toLowerCase().startsWith("attr:")) {
            String attrName = attribute.substring(5);
            value = target.attr(attrName);
        } else {
            value = target.attr(attribute);
        }
        if (("src".equalsIgnoreCase(attribute) || "href".equalsIgnoreCase(attribute)
                || "LINK".equalsIgnoreCase(selector.getDataType())) && StringUtils.hasText(value) && !value.startsWith("http")) {
            value = resolveAbsoluteUrl(baseUrl, value);
        }
        return value;
    }

    private String extractFuzzyValue(TaskWebCrawlSelector selector, Element context, String baseUrl) {
        String expr = selector.getSelectorValue();
        if (!StringUtils.hasText(expr)) {
            return null;
        }
        String css = expr;
        String keyword = null;
        int pipeIdx = expr.indexOf('|');
        if (pipeIdx >= 0) {
            css = expr.substring(0, pipeIdx).trim();
            keyword = expr.substring(pipeIdx + 1).trim();
        }
        if (!StringUtils.hasText(css)) {
            return null;
        }
        Elements elements = context.select(css);
        String lowerKeyword = StringUtils.hasText(keyword) ? keyword.toLowerCase() : null;
        for (Element element : elements) {
            if (lowerKeyword == null || element.text().toLowerCase().contains(lowerKeyword)) {
                return extractElementAttribute(selector, element, baseUrl);
            }
        }
        return null;
    }

    private Object extractJsonValue(TaskWebCrawlSelector selector) {
        JsonNode node = currentJsonResponse.get();
        if (node == null || node.isMissingNode()) {
            return null;
        }
        String path = selector.getSelectorValue();
        if (!StringUtils.hasText(path)) {
            path = "$";
        }
        String normalizedPath = normalizeJsonPath(path);
        try {
            String json = node.toString();
            Object result = JsonPath.read(json, normalizedPath);
            if (result == null) {
                return null;
            }
            return convertJsonPathResult(result);
        } catch (PathNotFoundException e) {
            log.debug("JSONPath 未匹配 [{}]: {}", selector.getFieldName(), normalizedPath);
            return null;
        } catch (Exception e) {
            log.warn("JSONPath 解析失败 [{}]: {}", selector.getFieldName(), e.getMessage());
            return null;
        }
    }

    private String normalizeJsonPath(String path) {
        String trimmed = path.trim();
        if ("$".equals(trimmed)) {
            return "$";
        }
        if (trimmed.startsWith("$")) {
            return trimmed;
        }
        if (trimmed.startsWith(".")) {
            return "$" + trimmed;
        }
        return "$." + trimmed;
    }

    private Object convertJsonPathResult(Object result) {
        if (result == null) {
            return null;
        }
        if (result instanceof Map<?, ?> map) {
            Map<String, Object> converted = new LinkedHashMap<>();
            map.forEach((k, v) -> converted.put(String.valueOf(k), convertJsonPathResult(v)));
            return converted;
        }
        if (result instanceof List<?> list) {
            List<Object> converted = new ArrayList<>(list.size());
            for (Object item : list) {
                converted.add(convertJsonPathResult(item));
            }
            return converted;
        }
        if (result instanceof String || result instanceof Number || result instanceof Boolean) {
            return result;
        }
        return result.toString();
    }

    private String extractXPathValue(TaskWebCrawlSelector selector, Document document) throws Exception {
        org.jsoup.helper.W3CDom w3cDom = new org.jsoup.helper.W3CDom();
        org.w3c.dom.Document w3cDoc = w3cDom.fromJsoup(document);
        XPath xpath = XPathFactory.newInstance().newXPath();
        Object result = xpath.evaluate(selector.getSelectorValue(), w3cDoc, XPathConstants.STRING);
        return result != null ? result.toString() : null;
    }

    private String extractRegexValue(TaskWebCrawlSelector selector, String html) {
        Pattern pattern = Pattern.compile(selector.getSelectorValue(), Pattern.DOTALL);
        Matcher matcher = pattern.matcher(html);
        if (matcher.find()) {
            if (matcher.groupCount() >= 1) {
                return matcher.group(1);
            }
            return matcher.group(0);
        }
        return null;
    }

    private Object convertDataType(String value, String dataType) {
        if (!StringUtils.hasText(dataType)) {
            return value;
        }
        return switch (dataType.toUpperCase()) {
            case "NUMBER" -> {
                try {
                    if (value.contains(".")) {
                        yield Double.parseDouble(value.replace(",", ""));
                    }
                    yield Long.parseLong(value.replace(",", ""));
                } catch (NumberFormatException e) {
                    yield value;
                }
            }
            case "DATE" -> value;
            case "LINK", "HTML", "STRING" -> value;
            default -> value;
        };
    }

    private String resolveNextUrl(TaskWebCrawlConfig config, Document document, String currentUrl,
                                  int nextPage, Map<String, Object> params) {
        String paginationType = config.getPaginationType();
        if ("URL_TEMPLATE".equalsIgnoreCase(paginationType)) {
            String template = config.getPaginationUrlTemplate();
            if (!StringUtils.hasText(template)) {
                return null;
            }
            Map<String, Object> pageParams = new LinkedHashMap<>(params);
            pageParams.put("page", nextPage);
            String path = PlaceholderUtils.replacePlaceholders(template, pageParams);
            return resolveAbsoluteUrl(currentUrl, path);
        }
        if ("SELECTOR".equalsIgnoreCase(paginationType)) {
            String selector = config.getPaginationSelector();
            if (!StringUtils.hasText(selector)) {
                return null;
            }
            Elements elements = document.select(selector);
            if (elements.isEmpty()) {
                return null;
            }
            Element next = elements.first();
            String href = next != null ? next.absUrl("href") : null;
            if (StringUtils.hasText(href)) {
                return href;
            }
            return next != null ? next.attr("data-url") : null;
        }
        return null;
    }

    private String applySshTunnelToUrl(String url, SshTunnel tunnel) {
        if (tunnel == null || !StringUtils.hasText(url)) {
            return url;
        }
        try {
            URL parsed = new URL(url);
            return new URL(parsed.getProtocol(), "127.0.0.1", tunnel.getLocalPort(), parsed.getFile()).toString();
        } catch (MalformedURLException e) {
            log.warn("替换 SSH 隧道 URL 失败: {}", url, e);
            return url;
        }
    }

    private String resolveAbsoluteUrl(String baseUrl, String relativeUrl) {
        if (!StringUtils.hasText(relativeUrl)) {
            return null;
        }
        if (relativeUrl.startsWith("http://") || relativeUrl.startsWith("https://")) {
            return relativeUrl;
        }
        try {
            return new URL(new URL(baseUrl), relativeUrl).toString();
        } catch (MalformedURLException e) {
            return relativeUrl;
        }
    }

    private String appendQueryParams(String url, Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return url;
        }
        StringBuilder sb = new StringBuilder(url);
        sb.append(url.contains("?") ? "&" : "?");
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) {
                sb.append("&");
            }
            first = false;
            sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            sb.append("=");
            sb.append(URLEncoder.encode(entry.getValue() != null ? entry.getValue() : "", StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private Map<String, Object> mergeParams(TaskWebCrawlConfig config, Map<String, Object> params) {
        Map<String, Object> merged = new LinkedHashMap<>();
        if (StringUtils.hasText(config.getCustomParams())) {
            try {
                Map<String, Object> customParams = objectMapper.readValue(config.getCustomParams(), new TypeReference<Map<String, Object>>() {
                });
                if (customParams != null) {
                    merged.putAll(customParams);
                }
            } catch (Exception e) {
                log.warn("爬取配置 customParams 解析失败, crawlId={}: {}", config.getId(), e.getMessage());
            }
        }
        if (params != null) {
            merged.putAll(params);
        }
        return merged;
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
            return map.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            e -> e.getValue() != null ? String.valueOf(e.getValue()) : "",
                            (a, b) -> a,
                            LinkedHashMap::new
                    ));
        } catch (Exception e) {
            if (logWarning) {
                log.warn("解析 JSON 失败: {}", e.getMessage());
            }
            return new LinkedHashMap<>();
        }
    }

    private Connection.Method parseMethod(String method) {
        if (!StringUtils.hasText(method)) {
            return Connection.Method.GET;
        }
        try {
            return Connection.Method.valueOf(method.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Connection.Method.GET;
        }
    }

    public SshConfig buildSshConfig(TaskWebCrawlConfig config) {
        SshConfig sshConfig = new SshConfig();

        // SSH 服务器（目标服务所在机器）
        sshConfig.setHost(config.getSshHost());
        sshConfig.setPort(config.getSshPort());
        sshConfig.setUsername(config.getSshUsername());
        sshConfig.setAuthType(config.getSshAuthType());
        boolean useKey = "KEY".equalsIgnoreCase(config.getSshAuthType());
        if (useKey) {
            sshConfig.setPrivateKey(config.getSshPrivateKey());
            sshConfig.setPassphrase(config.getSshPassphrase());
            sshConfig.setPassword(null);
        } else {
            sshConfig.setPassword(config.getSshPassword());
            sshConfig.setPrivateKey(null);
            sshConfig.setPassphrase(null);
        }

        // 多跳链路（跳板机 / 代理机），用于打通到 SSH 服务器的网络通道
        if (Integer.valueOf(1).equals(config.getSshJumpHostEnabled())) {
            sshConfig.setHops(config.getSshHops());
        }

        sshConfig.setLocalPort(config.getSshLocalPort());

        String remoteHost = config.getSshRemoteHost();
        Integer remotePort = config.getSshRemotePort();
        URL parsedUrl = parseRequestUrl(config.getRequestUrl());
        if (!StringUtils.hasText(remoteHost)) {
            remoteHost = parsedUrl != null ? parsedUrl.getHost() : null;
        }
        if (remotePort == null) {
            if (parsedUrl != null) {
                remotePort = parsedUrl.getPort() != -1 ? parsedUrl.getPort() : parsedUrl.getDefaultPort();
            }
            if (remotePort == null) {
                remotePort = 80;
            }
        }
        if (!StringUtils.hasText(remoteHost)) {
            throw new IllegalArgumentException("SSH 隧道目标主机未配置，且无法从请求 URL 解析");
        }
        sshConfig.setRemoteHost(remoteHost);
        sshConfig.setRemotePort(remotePort);
        return sshConfig;
    }

    private URL parseRequestUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        try {
            String normalized = replaceRequestVariables(url, null);
            if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
                normalized = "http://" + normalized;
            }
            return new URL(normalized);
        } catch (MalformedURLException e) {
            log.warn("解析请求 URL 失败: {}", url, e);
            return null;
        }
    }

    private String replaceRequestVariables(String text, Map<String, Object> params) {
        return PlaceholderUtils.replacePlaceholders(text, params);
    }

    private void decryptSensitiveFields(TaskWebCrawlConfig config) {
        if (StringUtils.hasText(config.getCookies())) {
            config.setCookies(CryptoUtil.decryptIfNeeded(config.getCookies()));
        }
        if (StringUtils.hasText(config.getAuthConfig())) {
            config.setAuthConfig(CryptoUtil.decryptIfNeeded(config.getAuthConfig()));
        }
        if (StringUtils.hasText(config.getSshPassword())) {
            config.setSshPassword(CryptoUtil.decryptIfNeeded(config.getSshPassword()));
        }
        if (StringUtils.hasText(config.getSshPrivateKey())) {
            config.setSshPrivateKey(CryptoUtil.decryptIfNeeded(config.getSshPrivateKey()));
        }
        if (StringUtils.hasText(config.getSshPassphrase())) {
            config.setSshPassphrase(CryptoUtil.decryptIfNeeded(config.getSshPassphrase()));
        }
        if (StringUtils.hasText(config.getProxyPassword())) {
            config.setProxyPassword(CryptoUtil.decryptIfNeeded(config.getProxyPassword()));
        }
        decryptSshHops(config.getSshHops());
    }

    private void decryptSshHops(List<SshHopConfig> hops) {
        if (CollectionUtils.isEmpty(hops)) {
            return;
        }
        for (SshHopConfig hop : hops) {
            if (StringUtils.hasText(hop.getPassword())) {
                hop.setPassword(CryptoUtil.decryptIfNeeded(hop.getPassword()));
            }
            if (StringUtils.hasText(hop.getPrivateKey())) {
                hop.setPrivateKey(CryptoUtil.decryptIfNeeded(hop.getPrivateKey()));
            }
            if (StringUtils.hasText(hop.getPassphrase())) {
                hop.setPassphrase(CryptoUtil.decryptIfNeeded(hop.getPassphrase()));
            }
        }
    }
}
