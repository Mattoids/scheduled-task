package com.mattoid.scheduled.controller;

import com.mattoid.scheduled.service.wecom.WeComAdminSsoService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 企业微信管理后台免登录多域反向代理。
 * <p>入口 {@code /wecom-proxy/entry?ticket=xxx} 校验 ticket 后签发会话 Cookie 并重定向到管理后台。</p>
 * <p>代理路径 {@code /wecom-proxy/{domainKey}/**} 将请求转发到对应上游域，自动注入 Cookie，
 * 并在 HTML 响应中注入 fetch/XHR 拦截脚本，使 SPA 的所有请求都经过代理。</p>
 */
@Slf4j
@RestController
@RequestMapping(WeComAdminSsoService.PROXY_BASE)
public class WeComAdminProxyController {

    /** 域映射：代理路径 key → 上游 origin */
    private static final Map<String, String> DOMAIN_MAP = Map.of(
            "work", "https://work.weixin.qq.com",
            "wwcdn", "https://wwcdn.weixin.qq.com"
    );

    /** 反向映射：上游 host → 代理路径 key */
    private static final Map<String, String> HOST_TO_KEY = Map.of(
            "work.weixin.qq.com", "work",
            "wwcdn.weixin.qq.com", "wwcdn"
    );

    /** 被屏蔽的域（监控/统计/非功能必需，返回空响应） */
    private static final Set<String> BLOCKED_HOSTS = Set.of(
            "aegis.qq.com", "report.url.cn", "badjs.weixinbridge.com",
            "pingjs.qq.com", "tajs.qq.com", "js.aq.qq.com",
            "cdn-go.cn", "p.qlogo.cn"
    );

    /** 不应透传给上游的请求头 */
    private static final Set<String> HOP_BY_HOP_REQUEST_HEADERS = Set.of(
            "host", "connection", "keep-alive", "proxy-connection", "transfer-encoding",
            "upgrade", "content-length", "accept-encoding", "cookie", "te", "trailer");

    /** 不应透传给浏览器的响应头 */
    private static final Set<String> HOP_BY_HOP_RESPONSE_HEADERS = Set.of(
            "transfer-encoding", "connection", "content-length", "keep-alive", "proxy-authenticate",
            "proxy-authorization", "te", "trailer", "upgrade",
            "content-security-policy", "x-frame-options", "strict-transport-security");

    private final WeComAdminSsoService weComAdminSsoService;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public WeComAdminProxyController(WeComAdminSsoService weComAdminSsoService) {
        this.weComAdminSsoService = weComAdminSsoService;
    }

    /** 免登录入口：消费 ticket，签发会话，跳转到管理后台（session token 通过 URL 传递，首次请求后写入 Cookie）。 */
    @GetMapping("/entry")
    public void entry(@RequestParam String ticket, HttpServletResponse response) throws IOException {
        String sessionToken = weComAdminSsoService.consumeTicket(ticket);
        if (sessionToken == null) {
            log.warn("[PROXY-ENTRY] ticket 无效或已过期: {}", ticket.substring(0, Math.min(8, ticket.length())));
            writeHtmlError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "免登录失败", "免登录票据无效或已过期，请重新从系统页面进入。");
            return;
        }
        log.info("[PROXY-ENTRY] 会话已创建: token={}...", sessionToken.substring(0, 8));
        // 同时设置 Cookie 和 URL 参数，确保首次请求一定能找到会话
        Cookie cookie = new Cookie(WeComAdminSsoService.SESSION_COOKIE, sessionToken);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge((int) Duration.ofHours(2).toSeconds());
        response.addCookie(cookie);
        response.sendRedirect(WeComAdminSsoService.PROXY_BASE + "/work/wework_admin/frame?_s=" + sessionToken);
    }

    /** 被屏蔽域的请求，返回空响应。 */
    @RequestMapping("/blocked/**")
    public void blocked(HttpServletResponse response) throws IOException {
        response.setStatus(200);
        response.setContentType("application/json");
        response.getWriter().write("{}");
    }

    /** 多域反向代理：将 /wecom-proxy/{domainKey}/** 转发到对应上游域。 */
    @RequestMapping("/{domainKey}/**")
    public void proxy(@PathVariable String domainKey,
                      HttpServletRequest request, HttpServletResponse response) throws IOException {
        String upstream = DOMAIN_MAP.get(domainKey);
        if (upstream == null) {
            writeHtmlError(response, HttpServletResponse.SC_NOT_FOUND,
                    "未知代理域", "不支持的代理域: " + domainKey);
            return;
        }

        // 优先从 URL 参数 _s 解析会话（首次重定向），其次从 Cookie
        WeComAdminSsoService.ProxySession session = null;
        String sessionFromUrl = request.getParameter("_s");
        String sessionSource = null;
        if (StringUtils.hasText(sessionFromUrl)) {
            session = weComAdminSsoService.resolveSession(sessionFromUrl);
            if (session != null) {
                sessionSource = "_s";
                // 设置 Cookie 供后续请求使用，不做重定向直接代理（避免浏览器不携带 302 响应中的 Cookie）
                Cookie cookie = new Cookie(WeComAdminSsoService.SESSION_COOKIE, sessionFromUrl);
                cookie.setPath("/");
                cookie.setHttpOnly(true);
                cookie.setMaxAge((int) Duration.ofHours(2).toSeconds());
                response.addCookie(cookie);
            }
        }
        if (session == null) {
            session = resolveSession(request);
            if (session != null) {
                sessionSource = "cookie";
            }
        }
        if (session == null) {
            StringBuilder cookieDebug = new StringBuilder();
            Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (Cookie c : cookies) {
                    if (cookieDebug.length() > 0) cookieDebug.append(", ");
                    cookieDebug.append(c.getName()).append("=").append(c.getValue().substring(0, Math.min(8, c.getValue().length())));
                }
            }
            log.warn("[PROXY] 会话未找到: domainKey={}, path={}, _s={}, cookies=[{}]",
                    domainKey, request.getRequestURI(), sessionFromUrl, cookieDebug);
            writeHtmlError(response, HttpServletResponse.SC_UNAUTHORIZED,
                    "会话已过期", "免登录会话已过期，请重新从系统页面进入。");
            return;
        }
        log.info("[PROXY] 会话已解析: domainKey={}, path={}, source={}",
                domainKey, request.getRequestURI(), sessionSource);

        String upstreamHost = URI.create(upstream).getHost();
        WeComAdminSsoService.SessionCookieJar jar = session.cookieJar();

        String path = extractProxyPath(request);
        String query = stripSessionParam(request.getQueryString());
        String targetUrl = upstream + path + (StringUtils.hasText(query) ? "?" + query : "");
        String origin = resolveOrigin(request);

        try {
            HttpResponse<byte[]> upstreamResp = httpClient.send(
                    buildUpstreamRequest(request, targetUrl, jar, upstreamHost),
                    HttpResponse.BodyHandlers.ofByteArray());
            // 存储上游 Set-Cookie
            for (String setCookie : upstreamResp.headers().allValues("Set-Cookie")) {
                jar.storeFromSetCookie(upstreamHost, setCookie);
            }
            if (upstreamResp.statusCode() >= 400) {
                log.warn("[PROXY] 上游返回错误: domainKey={}, path={}, status={}, targetUrl={}",
                        domainKey, path, upstreamResp.statusCode(), targetUrl);
            }
            // 检查 JSON 错误响应（如参数错误）
            String respContentType = upstreamResp.headers().firstValue("Content-Type").orElse("");
            if (respContentType.contains("json") && upstreamResp.body() != null
                    && upstreamResp.body().length > 0 && upstreamResp.body().length < 2000) {
                String bodyStr = new String(upstreamResp.body(), StandardCharsets.UTF_8);
                if (bodyStr.contains("\"errcode\"") && !bodyStr.contains("\"errcode\":0")
                        && !bodyStr.contains("\"errcode\": 0")) {
                    log.warn("[PROXY] 上游 JSON 错误: path={}, targetUrl={}, body={}",
                            path, targetUrl, bodyStr);
                }
            }
            writeResponse(response, upstreamResp, origin, domainKey);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            writeHtmlError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "请求被中断", "代理请求被中断，请重试。");
        } catch (Exception e) {
            log.warn("代理请求失败: {} -> {}", path, e.getMessage());
            writeHtmlError(response, HttpServletResponse.SC_BAD_GATEWAY,
                    "访问失败", "访问企业微信管理后台失败: " + e.getMessage());
        }
    }

    /** 从查询字符串中移除 _s 参数，避免内部 session token 泄漏到上游。 */
    private String stripSessionParam(String query) {
        if (!StringUtils.hasText(query)) return query;
        String[] parts = query.split("&");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.startsWith("_s=") || part.equals("_s")) continue;
            if (sb.length() > 0) sb.append("&");
            sb.append(part);
        }
        return sb.length() > 0 ? sb.toString() : null;
    }

    private WeComAdminSsoService.ProxySession resolveSession(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (WeComAdminSsoService.SESSION_COOKIE.equals(cookie.getName())) {
                WeComAdminSsoService.ProxySession session = weComAdminSsoService.resolveSession(cookie.getValue());
                if (session != null) {
                    return session;
                }
            }
        }
        return null;
    }

    private String extractProxyPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String prefix = request.getContextPath() + WeComAdminSsoService.PROXY_BASE;
        String rest = uri.substring(prefix.length());
        // rest = /{domainKey}/actual/path
        int secondSlash = rest.indexOf('/', 1);
        if (secondSlash < 0) return "/";
        return rest.substring(secondSlash);
    }

    private HttpRequest buildUpstreamRequest(HttpServletRequest request, String targetUrl,
                                             WeComAdminSsoService.SessionCookieJar jar,
                                             String upstreamHost) throws IOException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(targetUrl))
                .timeout(Duration.ofSeconds(30));

        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            String lower = name.toLowerCase(Locale.ROOT);
            if (HOP_BY_HOP_REQUEST_HEADERS.contains(lower)) {
                continue;
            }
            Enumeration<String> values = request.getHeaders(name);
            while (values.hasMoreElements()) {
                String value = values.nextElement();
                if ("referer".equals(lower)) {
                    value = rewriteUrlForUpstream(value, upstreamHost);
                }
                builder.header(name, value);
            }
        }

        // Cookie 从服务端 Cookie Jar 注入
        String cookieHeader = jar.getCookieHeader(upstreamHost);
        if (StringUtils.hasText(cookieHeader)) {
            builder.header("Cookie", cookieHeader);
        }

        String method = request.getMethod().toUpperCase(Locale.ROOT);
        if ("GET".equals(method) || "HEAD".equals(method) || "DELETE".equals(method)) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            try (InputStream in = request.getInputStream()) {
                builder.method(method, HttpRequest.BodyPublishers.ofByteArray(in.readAllBytes()));
            }
        }
        return builder.build();
    }

    /** 将 Referer 中的代理地址替换回上游地址，并移除内部 _s 参数。 */
    private String rewriteUrlForUpstream(String url, String upstreamHost) {
        for (Map.Entry<String, String> entry : HOST_TO_KEY.entrySet()) {
            String proxyPath = WeComAdminSsoService.PROXY_BASE + "/" + entry.getValue();
            if (url.contains(proxyPath)) {
                String rewritten = url.replace(proxyPath, "").replaceFirst(
                        "https?://[^/]+", "https://" + upstreamHost);
                // 移除 _s 参数，避免内部 token 泄漏到上游
                int qIdx = rewritten.indexOf('?');
                if (qIdx >= 0) {
                    String query = rewritten.substring(qIdx + 1);
                    String stripped = stripSessionParam(query);
                    rewritten = rewritten.substring(0, qIdx)
                            + (StringUtils.hasText(stripped) ? "?" + stripped : "");
                }
                return rewritten;
            }
        }
        return url;
    }

    private void writeResponse(HttpServletResponse response,
                               HttpResponse<byte[]> upstream, String origin,
                               String domainKey) throws IOException {
        response.setStatus(upstream.statusCode());

        String contentType = upstream.headers().firstValue("Content-Type").orElse("");
        boolean isHtml = contentType.toLowerCase(Locale.ROOT).contains("text/html");
        boolean isCss = contentType.toLowerCase(Locale.ROOT).contains("text/css");
        boolean isJs = contentType.toLowerCase(Locale.ROOT).contains("javascript");
        boolean isTextual = isHtml || isCss || isJs
                || contentType.toLowerCase(Locale.ROOT).contains("application/json")
                || contentType.toLowerCase(Locale.ROOT).contains("application/xml")
                || contentType.toLowerCase(Locale.ROOT).contains("text/xml")
                || contentType.toLowerCase(Locale.ROOT).contains("image/svg");

        boolean gzipEncoded = upstream.headers().firstValue("Content-Encoding")
                .map(v -> v.toLowerCase(Locale.ROOT).contains("gzip"))
                .orElse(false);

        for (Map.Entry<String, List<String>> entry : upstream.headers().map().entrySet()) {
            String name = entry.getKey();
            String lower = name.toLowerCase(Locale.ROOT);
            if (HOP_BY_HOP_RESPONSE_HEADERS.contains(lower)) {
                continue;
            }
            if ("set-cookie".equals(lower)) {
                continue; // Cookie 由服务端 Cookie Jar 管理，不透传给浏览器
            }
            for (String value : entry.getValue()) {
                if ("location".equals(lower)) {
                    response.addHeader("Location", rewriteLocationHeader(value, origin));
                } else {
                    response.addHeader(name, value);
                }
            }
        }

        byte[] body = upstream.body();
        if (body == null || body.length == 0) return;

        if (!gzipEncoded && isTextual) {
            Charset charset = resolveCharset(contentType);
            String text = new String(body, charset);
            text = rewriteBodyUrls(text, origin);
            if (isHtml) {
                text = injectInterceptorScript(text, origin);
            }
            response.getOutputStream().write(text.getBytes(charset));
        } else {
            response.getOutputStream().write(body);
        }
    }

    /** 重写响应体中的上游 URL 为代理路径。 */
    private String rewriteBodyUrls(String text, String origin) {
        String base = origin + WeComAdminSsoService.PROXY_BASE;
        String proxyWork = WeComAdminSsoService.PROXY_BASE + "/work";
        // 绝对 URL
        text = text.replace("https://work.weixin.qq.com", base + "/work");
        text = text.replace("https://wwcdn.weixin.qq.com", base + "/wwcdn");
        // 协议相对 URL
        text = text.replace("//work.weixin.qq.com", base + "/work");
        text = text.replace("//wwcdn.weixin.qq.com", base + "/wwcdn");
        // 屏蔽域
        for (String blocked : BLOCKED_HOSTS) {
            text = text.replace("https://" + blocked, base + "/blocked");
            text = text.replace("//" + blocked, base + "/blocked");
        }
        // 相对路径（同源路径重写到 work 域代理）
        text = text.replace("\"/wework_admin", "\"" + proxyWork + "/wework_admin");
        text = text.replace("'/wework_admin", "'" + proxyWork + "/wework_admin");
        text = text.replace("(/wework_admin", "(" + proxyWork + "/wework_admin");
        text = text.replace("\"/cgi-bin", "\"" + proxyWork + "/cgi-bin");
        text = text.replace("'/cgi-bin", "'" + proxyWork + "/cgi-bin");
        return text;
    }

    /** 在 HTML 的 <head> 后注入 fetch/XHR 拦截脚本。 */
    private String injectInterceptorScript(String html, String origin) {
        String script = buildInterceptorScript(origin);
        // 在 <head> 后立即注入，确保最先执行
        String lower = html.toLowerCase(Locale.ROOT);
        int headEnd = lower.indexOf("<head>");
        if (headEnd >= 0) {
            int insertAt = headEnd + "<head>".length();
            // 跳过 <head> 可能的属性
            int gt = html.indexOf('>', headEnd);
            if (gt >= 0) insertAt = gt + 1;
            return html.substring(0, insertAt) + script + html.substring(insertAt);
        }
        // 没有 <head>，在开头注入
        return script + html;
    }

    /**
     * 构建拦截脚本：patch fetch/XHR/history.pushState/<a>点击，
     * 将所有请求重写到代理路径；未知外部域直接屏蔽。
     */
    private String buildInterceptorScript(String origin) {
        String proxyBase = WeComAdminSsoService.PROXY_BASE;
        StringBuilder blockedHostsJs = new StringBuilder("{");
        boolean first = true;
        for (String h : BLOCKED_HOSTS) {
            if (!first) blockedHostsJs.append(",");
            blockedHostsJs.append("'").append(h).append("':1");
            first = false;
        }
        blockedHostsJs.append("}");

        return "<script>(function(){\n"
                + "var P='" + proxyBase + "';\n"
                + "var D={'work.weixin.qq.com':'work','wwcdn.weixin.qq.com':'wwcdn'};\n"
                + "var B=" + blockedHostsJs + ";\n"
                + "function rw(u){\n"
                + "  try{\n"
                + "    var a=new URL(u,location.href);\n"
                + "    if(B[a.hostname])return location.origin+P+'/blocked'+a.pathname;\n"
                + "    var k=D[a.hostname];\n"
                + "    if(k)return location.origin+P+'/'+k+a.pathname+a.search+a.hash;\n"
                + "    if(a.origin===location.origin&&!a.pathname.startsWith(P+'/')&&!a.pathname.startsWith('/api/'))\n"
                + "      return location.origin+P+'/work'+a.pathname+a.search+a.hash;\n"
                + "    if(a.origin!==location.origin)return location.origin+P+'/blocked'+a.pathname;\n"
                + "  }catch(e){}\n"
                + "  return u;\n"
                + "}\n"
                // fetch
                + "var of=window.fetch;\n"
                + "window.fetch=function(i,init){\n"
                + "  if(typeof i==='string')i=rw(i);\n"
                + "  else if(i instanceof Request){var r=rw(i.url);if(r!==i.url)i=new Request(r,i);}\n"
                + "  return of.call(this,i,init);\n"
                + "};\n"
                // XHR
                + "var oo=XMLHttpRequest.prototype.open;\n"
                + "XMLHttpRequest.prototype.open=function(m,u){\n"
                + "  if(arguments.length>1)arguments[1]=rw(u);\n"
                + "  return oo.apply(this,arguments);\n"
                + "};\n"
                // history.pushState / replaceState
                + "var op=history.pushState;\n"
                + "history.pushState=function(s,t,u){\n"
                + "  if(u)arguments[2]=rw(u);\n"
                + "  return op.apply(this,arguments);\n"
                + "};\n"
                + "var ors=history.replaceState;\n"
                + "history.replaceState=function(s,t,u){\n"
                + "  if(u)arguments[2]=rw(u);\n"
                + "  return ors.apply(this,arguments);\n"
                + "};\n"
                // <a> click interception
                + "document.addEventListener('click',function(e){\n"
                + "  var a=e.target.closest('a[href]');\n"
                + "  if(!a)return;\n"
                + "  var h=a.getAttribute('href');\n"
                + "  if(!h||h.startsWith('#')||h.startsWith('javascript:'))return;\n"
                + "  var r=rw(h);\n"
                + "  if(r!==h){e.preventDefault();e.stopPropagation();window.location.href=r;}\n"
                + "},true);\n"
                + "})();</script>";
    }

    private String rewriteLocationHeader(String url, String origin) {
        for (Map.Entry<String, String> entry : HOST_TO_KEY.entrySet()) {
            String host = entry.getKey();
            String key = entry.getValue();
            if (url.startsWith("https://" + host)) {
                return origin + WeComAdminSsoService.PROXY_BASE + "/" + key
                        + url.substring(("https://" + host).length());
            }
        }
        if (url.startsWith("/")) {
            return WeComAdminSsoService.PROXY_BASE + "/work" + url;
        }
        return url;
    }

    private String resolveOrigin(HttpServletRequest request) {
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        String scheme = StringUtils.hasText(forwardedProto) ? forwardedProto : request.getScheme();
        String forwardedHost = request.getHeader("X-Forwarded-Host");
        if (StringUtils.hasText(forwardedHost)) {
            return scheme + "://" + forwardedHost;
        }
        int port = request.getServerPort();
        boolean defaultPort = ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
        return scheme + "://" + request.getServerName() + (defaultPort ? "" : ":" + port);
    }

    private Charset resolveCharset(String contentType) {
        for (String part : contentType.split(";")) {
            String trimmed = part.trim().toLowerCase(Locale.ROOT);
            if (trimmed.startsWith("charset=")) {
                try {
                    return Charset.forName(trimmed.substring("charset=".length()).trim());
                } catch (Exception ignored) {
                    return StandardCharsets.UTF_8;
                }
            }
        }
        return StandardCharsets.UTF_8;
    }

    /** 输出友好的 HTML 错误页面。 */
    private void writeHtmlError(HttpServletResponse response, int status, String title, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("text/html;charset=UTF-8");
        String html = """
                <!DOCTYPE html>
                <html>
                <head><meta charset="UTF-8"><title>%s</title>
                <style>
                  body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                         display: flex; justify-content: center; align-items: center; min-height: 100vh;
                         margin: 0; background: #f5f7fa; }
                  .error-card { background: white; border-radius: 8px; padding: 40px; max-width: 500px;
                                box-shadow: 0 2px 12px rgba(0,0,0,0.1); text-align: center; }
                  .error-code { font-size: 48px; font-weight: 700; color: #f56c6c; margin-bottom: 16px; }
                  .error-title { font-size: 20px; font-weight: 600; color: #303133; margin-bottom: 12px; }
                  .error-message { font-size: 14px; color: #606266; line-height: 1.6; margin-bottom: 24px; }
                  .error-action { color: #409eff; text-decoration: none; font-size: 14px; cursor: pointer; }
                </style></head>
                <body><div class="error-card">
                  <div class="error-code">%d</div>
                  <div class="error-title">%s</div>
                  <div class="error-message">%s</div>
                  <a class="error-action" onclick="window.close()">关闭页面</a>
                </div></body></html>
                """.formatted(title, status, title, message);
        response.getWriter().write(html);
    }
}
