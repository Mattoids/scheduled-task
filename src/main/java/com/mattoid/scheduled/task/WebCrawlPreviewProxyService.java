package com.mattoid.scheduled.task;

import com.mattoid.scheduled.entity.TaskWebCrawlConfig;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 网页预览代理服务：将目标页面中的同域资源 URL 改写为后端代理地址，
 * 使 iframe 在 same-origin 下加载，避免浏览器 CORS 限制。
 */
@Slf4j
@Service
public class WebCrawlPreviewProxyService {

    private static final List<String> URL_ATTRIBUTES = List.of(
            "src", "href", "data-src", "poster", "action", "srcset", "background"
    );

    private static final Pattern CSS_URL_PATTERN = Pattern.compile(
            "url\\s*\\(\\s*([\"']?)([^\"')]+)\\1\\s*\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CSS_IMPORT_PATTERN = Pattern.compile(
            "@import\\s+(?:url\\s*\\(\\s*([\"']?)([^\"')]+)\\1\\s*\\)|([\"'])([^\"']+)\\3)", Pattern.CASE_INSENSITIVE);

    private final WebCrawlExecutor webCrawlExecutor;
    private final WebCrawlPreviewContextCache contextCache;

    public WebCrawlPreviewProxyService(WebCrawlExecutor webCrawlExecutor,
                                       WebCrawlPreviewContextCache contextCache) {
        this.webCrawlExecutor = webCrawlExecutor;
        this.contextCache = contextCache;
    }

    public PreviewHtmlResult rewrite(TaskWebCrawlConfig config) {
        WebCrawlPreviewResult preview = webCrawlExecutor.preview(config, null);
        int statusCode = preview.getStatusCode() != null ? preview.getStatusCode() : 500;
        if (!preview.isSuccess()) {
            return new PreviewHtmlResult(false, statusCode, preview.getMessage(), null, null);
        }
        String html = preview.getContent();
        if (!StringUtils.hasText(html)) {
            return new PreviewHtmlResult(false, statusCode, "页面内容为空", null, null);
        }

        String baseUrl = config.getRequestUrl();
        String token = contextCache.put(config);
        String rewritten;
        if (isHtml(html)) {
            rewritten = rewriteHtml(html, baseUrl, token);
        } else {
            rewritten = "<pre>" + escapeHtml(html) + "</pre>";
        }
        return new PreviewHtmlResult(true, statusCode, preview.getMessage(), rewritten, token);
    }

    public ResourceProxyResult proxyResource(String token, String targetUrl) throws Exception {
        WebCrawlPreviewContextCache.PreviewContext ctx = contextCache.get(token);
        if (ctx == null) {
            throw new IllegalArgumentException("预览令牌已过期或无效");
        }
        WebCrawlExecutor.ResourceResponse response = webCrawlExecutor.fetchResource(ctx.config(), targetUrl);
        String contentType = response.contentType();
        byte[] body = response.body();
        if (isCss(contentType, targetUrl)) {
            String css = new String(body, StandardCharsets.UTF_8);
            String rewritten = rewriteCss(css, targetUrl, token);
            body = rewritten.getBytes(StandardCharsets.UTF_8);
            if (contentType == null) {
                contentType = "text/css";
            }
        }
        return new ResourceProxyResult(contentType, body);
    }

    public String rewriteCss(String css, String baseUrl, String token) {
        if (!StringUtils.hasText(css)) {
            return css;
        }
        String proxyBase = buildProxyUrl(token, "");
        css = rewriteCssUrls(css, baseUrl, token, proxyBase);
        css = rewriteCssImports(css, baseUrl, token, proxyBase);
        return css;
    }

    private String rewriteHtml(String html, String baseUrl, String token) {
        Document document = Jsoup.parse(html, baseUrl);
        Elements elements = document.select("*");
        for (Element element : elements) {
            for (String attr : URL_ATTRIBUTES) {
                if (element.hasAttr(attr)) {
                    String value = element.attr(attr);
                    String rewritten = rewriteAttributeValue(value, baseUrl, token, attr);
                    if (!value.equals(rewritten)) {
                        element.attr(attr, rewritten);
                    }
                }
            }
            // 内联样式
            String style = element.attr("style");
            if (StringUtils.hasText(style)) {
                element.attr("style", rewriteCss(style, baseUrl, token));
            }
            // <style> 标签内部 CSS
            if ("style".equalsIgnoreCase(element.tagName())) {
                String css = element.html();
                if (StringUtils.hasText(css)) {
                    element.html(rewriteCss(css, baseUrl, token));
                }
            }
        }

        // 为 <base> 标签补充 href，帮助相对路径解析
        Element base = document.selectFirst("base");
        if (base == null) {
            document.head().prepend("<base href=\"" + escapeHtml(baseUrl) + "\">");
        } else if (!base.hasAttr("href")) {
            base.attr("href", baseUrl);
        }

        return document.html();
    }

    private String rewriteAttributeValue(String value, String baseUrl, String token, String attr) {
        if (!StringUtils.hasText(value)) {
            return value;
        }
        if ("srcset".equalsIgnoreCase(attr)) {
            return rewriteSrcset(value, baseUrl, token);
        }
        String url = extractFirstUrl(value);
        if (!StringUtils.hasText(url)) {
            return value;
        }
        String resolved = resolveUrl(baseUrl, url);
        if (!shouldProxy(baseUrl, resolved)) {
            return value;
        }
        return buildProxyUrl(token, resolved);
    }

    private String rewriteSrcset(String srcset, String baseUrl, String token) {
        if (!StringUtils.hasText(srcset)) {
            return srcset;
        }
        StringBuilder sb = new StringBuilder();
        for (String part : srcset.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int spaceIdx = trimmed.indexOf(' ');
            String url = spaceIdx > 0 ? trimmed.substring(0, spaceIdx) : trimmed;
            String descriptor = spaceIdx > 0 ? trimmed.substring(spaceIdx) : "";
            String resolved = resolveUrl(baseUrl, url);
            if (shouldProxy(baseUrl, resolved)) {
                url = buildProxyUrl(token, resolved);
            }
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(url).append(descriptor);
        }
        return sb.toString();
    }

    private String rewriteCssUrls(String css, String baseUrl, String token, String proxyBase) {
        Matcher matcher = CSS_URL_PATTERN.matcher(css);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String quote = matcher.group(1);
            String url = matcher.group(2);
            String resolved = resolveUrl(baseUrl, url);
            String replacement;
            if (shouldProxy(baseUrl, resolved)) {
                replacement = "url(" + quote + escapeCssUrl(buildProxyUrl(token, resolved)) + quote + ")";
            } else {
                replacement = "url(" + quote + escapeCssUrl(resolved) + quote + ")";
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String rewriteCssImports(String css, String baseUrl, String token, String proxyBase) {
        Matcher matcher = CSS_IMPORT_PATTERN.matcher(css);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String quote;
            String url;
            boolean urlForm = matcher.group(2) != null;
            if (urlForm) {
                quote = matcher.group(1);
                url = matcher.group(2);
            } else {
                quote = matcher.group(3);
                url = matcher.group(4);
            }
            String resolved = resolveUrl(baseUrl, url);
            String escaped = escapeCssUrl(shouldProxy(baseUrl, resolved)
                    ? buildProxyUrl(token, resolved) : resolved);
            String replacement;
            if (urlForm) {
                String q = StringUtils.hasText(quote) ? quote : "\"";
                replacement = "@import url(" + q + escaped + q + ")";
            } else {
                replacement = "@import " + quote + escaped + quote;
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String extractFirstUrl(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("url(") && trimmed.endsWith(")")) {
            String inner = trimmed.substring(4, trimmed.length() - 1).trim();
            if (inner.length() >= 2 && (inner.charAt(0) == '\"' || inner.charAt(0) == '\'')) {
                inner = inner.substring(1, inner.length() - 1);
            }
            return inner;
        }
        return trimmed;
    }

    private String resolveUrl(String baseUrl, String url) {
        if (!StringUtils.hasText(url)) {
            return url;
        }
        if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("//")) {
            if (url.startsWith("//")) {
                return resolveProtocol(baseUrl) + ":" + url;
            }
            return url;
        }
        try {
            return new URL(new URL(baseUrl), url).toString();
        } catch (MalformedURLException e) {
            return url;
        }
    }

    private String resolveProtocol(String baseUrl) {
        if (baseUrl.startsWith("https://")) {
            return "https";
        }
        return "http";
    }

    private boolean shouldProxy(String baseUrl, String resolvedUrl) {
        if (!StringUtils.hasText(resolvedUrl)) {
            return false;
        }
        if (resolvedUrl.startsWith("data:") || resolvedUrl.startsWith("javascript:") || resolvedUrl.startsWith("mailto:")) {
            return false;
        }
        String baseHost = extractHost(baseUrl);
        String targetHost = extractHost(resolvedUrl);
        return baseHost != null && baseHost.equalsIgnoreCase(targetHost);
    }

    private String extractHost(String url) {
        try {
            return new URL(url).getHost();
        } catch (MalformedURLException e) {
            return null;
        }
    }

    private String buildProxyUrl(String token, String targetUrl) {
        if (!StringUtils.hasText(targetUrl)) {
            return "/api/task-crawl/preview-resource?token=" + encodeToken(token);
        }
        return "/api/task-crawl/preview-resource?token=" + encodeToken(token)
                + "&url=" + Base64.getUrlEncoder().withoutPadding().encodeToString(targetUrl.getBytes(StandardCharsets.UTF_8));
    }

    private String encodeToken(String token) {
        return token;
    }

    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }

    private String escapeCssUrl(String url) {
        if (url == null) {
            return "";
        }
        return url.replace("\\", "\\\\").replace("'", "\\'").replace("\"", "\\\"");
    }

    private boolean isCss(String contentType, String url) {
        if (StringUtils.hasText(contentType) && contentType.toLowerCase().contains("css")) {
            return true;
        }
        return url != null && url.toLowerCase().endsWith(".css");
    }

    private boolean isHtml(String content) {
        if (!StringUtils.hasText(content)) {
            return false;
        }
        String trimmed = content.trim().toLowerCase();
        if (trimmed.startsWith("<!doctype") || trimmed.startsWith("<html") || trimmed.startsWith("<body")) {
            return true;
        }
        return trimmed.contains("<html") || trimmed.contains("<body");
    }

    public record PreviewHtmlResult(boolean success, int statusCode, String message,
                                     String html, String token) {
    }

    public record ResourceProxyResult(String contentType, byte[] body) {
    }
}
