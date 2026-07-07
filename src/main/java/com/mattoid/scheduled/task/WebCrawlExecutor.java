package com.mattoid.scheduled.task;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mattoid.scheduled.datasource.SshConfig;
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
import java.nio.charset.StandardCharsets;
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

@Slf4j
@Component
public class WebCrawlExecutor {

    private static final String ENC_PREFIX = "ENC(";
    private static final int DEFAULT_TIMEOUT_MS = 30_000;

    private final ObjectMapper objectMapper;
    private final WebCrawlMediaDownloader mediaDownloader;
    private final WebDriverManager webDriverManager;
    private final SshTunnelManager sshTunnelManager;

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

            if ("DYNAMIC".equalsIgnoreCase(config.getRenderType())) {
                Document document = webDriverManager.fetchPage(config, actualUrl, mergedParams);
                return WebCrawlPreviewResult.success(200, "页面可访问", document.title(), document.html());
            }

            Connection connection = buildPreviewConnection(config, actualUrl, mergedParams);
            Connection.Response response = connection.execute();
            Document document = response.parse();
            int statusCode = response.statusCode();
            String title = document.title();
            boolean ok = statusCode >= 200 && statusCode < 400;
            if (!ok) {
                return WebCrawlPreviewResult.success(statusCode,
                        "请求返回非成功状态码: " + statusCode, title, document.html());
            }
            return WebCrawlPreviewResult.success(statusCode, "页面可访问", title, document.html());
        } catch (Exception e) {
            log.warn("网页爬取预览失败: {}", e.getMessage(), e);
            return WebCrawlPreviewResult.failure("预览失败: " + e.getMessage());
        } finally {
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
            Connection connection = buildPreviewConnection(config, actualUrl, mergedParams);
            Connection.Response response = connection.execute();
            return new ResourceResponse(response.contentType(), response.bodyAsBytes());
        } finally {
            WebCrawlProxyHelper.unbindAuth();
            if (tunnelId != null) {
                sshTunnelManager.closeTunnel(tunnelId);
            }
        }
    }

    public record ResourceResponse(String contentType, byte[] body) {
    }

    private Document fetchDocument(TaskWebCrawlConfig config, String url,
                                   Map<String, Object> params, SshTunnel tunnel) throws Exception {
        String actualUrl = applySshTunnelToUrl(url, tunnel);
        if ("DYNAMIC".equalsIgnoreCase(config.getRenderType())) {
            return webDriverManager.fetchPage(config, actualUrl, params);
        }
        Connection connection = buildConnection(config, actualUrl, params);
        return connection.execute().parse();
    }

    private Connection buildConnection(TaskWebCrawlConfig config, String url,
                                       Map<String, Object> params) throws IOException {
        Connection.Method method = parseMethod(config.getRequestMethod());
        Connection connection = Jsoup.connect(url)
                .method(method)
                .timeout(DEFAULT_TIMEOUT_MS)
                .followRedirects(true)
                .ignoreHttpErrors(false)
                .ignoreContentType(false);

        Proxy proxy = WebCrawlProxyHelper.createProxy(config);
        if (proxy != null) {
            connection.proxy(proxy);
        }

        applyHeaders(connection, config.getRequestHeaders(), params);
        applyCookies(connection, config.getCookies(), params);
        applyAuth(connection, config);

        Map<String, String> requestParams = parseJsonMap(replaceRequestVariables(config.getRequestParams(), params));
        if (!requestParams.isEmpty()) {
            connection.data(requestParams);
        }
        if (method == Connection.Method.POST || method == Connection.Method.PUT) {
            String body = replaceRequestVariables(config.getRequestBody(), params);
            if (StringUtils.hasText(body)) {
                if (StringUtils.hasText(config.getRequestContentType())) {
                    connection.requestBody(body);
                    connection.header("Content-Type", config.getRequestContentType());
                } else {
                    connection.requestBody(body);
                }
            }
        }
        return connection;
    }

    private Connection buildPreviewConnection(TaskWebCrawlConfig config, String url,
                                              Map<String, Object> params) throws IOException {
        Connection.Method method = parseMethod(config.getRequestMethod());
        Connection connection = Jsoup.connect(url)
                .method(method)
                .timeout(DEFAULT_TIMEOUT_MS)
                .followRedirects(true)
                .ignoreHttpErrors(true)
                .ignoreContentType(false);

        Proxy proxy = WebCrawlProxyHelper.createProxy(config);
        if (proxy != null) {
            connection.proxy(proxy);
        }

        applyHeaders(connection, config.getRequestHeaders(), params);
        applyCookies(connection, config.getCookies(), params);
        applyAuth(connection, config);

        Map<String, String> requestParams = parseJsonMap(replaceRequestVariables(config.getRequestParams(), params));
        if (!requestParams.isEmpty()) {
            connection.data(requestParams);
        }
        if (method == Connection.Method.POST || method == Connection.Method.PUT) {
            String body = replaceRequestVariables(config.getRequestBody(), params);
            if (StringUtils.hasText(body)) {
                if (StringUtils.hasText(config.getRequestContentType())) {
                    connection.requestBody(body);
                    connection.header("Content-Type", config.getRequestContentType());
                } else {
                    connection.requestBody(body);
                }
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

    private void applyCookies(Connection connection, String cookiesJson, Map<String, Object> params) {
        Map<String, String> cookies = parseJsonMap(replaceRequestVariables(cookiesJson, params));
        cookies.forEach(connection::cookie);
    }

    private void applyAuth(Connection connection, TaskWebCrawlConfig config) {
        String authType = config.getAuthType();
        if (!StringUtils.hasText(authType) || "NONE".equalsIgnoreCase(authType)) {
            return;
        }
        Map<String, String> auth = parseJsonMap(config.getAuthConfig());
        switch (authType.toUpperCase()) {
            case "BASIC" -> {
                String username = auth.get("username");
                String password = auth.get("password");
                if (StringUtils.hasText(username)) {
                    String credentials = Base64.getEncoder()
                            .encodeToString((username + ":" + (password != null ? password : "")).getBytes(StandardCharsets.UTF_8));
                    connection.header("Authorization", "Basic " + credentials);
                }
            }
            case "TOKEN" -> {
                String token = auth.get("token");
                String headerName = auth.getOrDefault("headerName", "Authorization");
                String prefix = auth.getOrDefault("prefix", "Bearer");
                if (StringUtils.hasText(token)) {
                    connection.header(headerName, (prefix + " " + token).trim());
                }
            }
            case "FORM" -> {
                // FORM 认证需要先登录获取 cookie，这里仅设置已有 cookie 即可
                // 如需先登录，可在 customParams 中设置 loginUrl/loginBody 由外部预处理
                log.warn("FORM 认证建议先通过预处理获取 Cookie 后配置到 cookies 字段");
            }
            case "OAUTH2" -> {
                String token = auth.get("accessToken");
                if (StringUtils.hasText(token)) {
                    connection.header("Authorization", "Bearer " + token);
                }
            }
            default -> log.warn("不支持的认证类型: {}", authType);
        }
    }

    private List<Map<String, Object>> extractData(TaskWebCrawlConfig config, Document document, String baseUrl) {
        List<TaskWebCrawlSelector> selectors = config.getSelectors();
        if (CollectionUtils.isEmpty(selectors)) {
            return Collections.emptyList();
        }

        // 找到第一个选择器匹配的元素集合，作为行基础
        TaskWebCrawlSelector rowSelector = findRowSelector(selectors);
        Elements rows;
        if (rowSelector != null && "CSS".equalsIgnoreCase(rowSelector.getSelectorType())) {
            rows = document.select(rowSelector.getSelectorValue());
        } else {
            rows = new Elements(document);
        }

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
        return data;
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

        String rawValue;
        try {
            rawValue = switch (selectorType.toUpperCase()) {
                case "CSS" -> extractCssValue(selector, context, baseUrl);
                case "XPATH" -> extractXPathValue(selector, document);
                case "REGEX" -> extractRegexValue(selector, context.html());
                default -> null;
            };
        } catch (Exception e) {
            log.warn("选择器提取失败 [{}]: {}", selector.getFieldName(), e.getMessage());
            rawValue = null;
        }
        if (!StringUtils.hasText(rawValue)) {
            return selector.getDefaultValue();
        }
        return convertDataType(rawValue, selector.getDataType());
    }

    private String extractCssValue(TaskWebCrawlSelector selector, Element context, String baseUrl) {
        Elements elements = context.select(selector.getSelectorValue());
        if (elements.isEmpty()) {
            return null;
        }
        Element target = elements.first();
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
            log.warn("解析 JSON 失败: {}", e.getMessage());
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

    private SshConfig buildSshConfig(TaskWebCrawlConfig config) {
        SshConfig sshConfig = new SshConfig();
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
        sshConfig.setLocalPort(config.getSshLocalPort());
        sshConfig.setRemoteHost(config.getSshRemoteHost());
        sshConfig.setRemotePort(config.getSshRemotePort());
        return sshConfig;
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
    }
}
