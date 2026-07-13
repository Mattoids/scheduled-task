package com.mattoid.scheduled.service.wecom;

import com.mattoid.scheduled.entity.WeComAdminAccount;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 企业微信管理后台免登录跳转（SSO）票据 + 会话管理。
 * <p>前端换取一次性 ticket（60 秒有效），打开代理入口时校验 ticket 并签发会话 Cookie（2 小时有效）。
 * 每个会话维护独立的 Cookie Jar（按域隔离），避免多账户串 Cookie。</p>
 */
@Slf4j
@Service
public class WeComAdminSsoService {

    /** 代理路径前缀 */
    public static final String PROXY_BASE = "/wecom-proxy";
    /** 会话 Cookie 名 */
    public static final String SESSION_COOKIE = "WECOM_PROXY_SESSION";

    private static final long TICKET_TTL_MILLIS = 60_000;
    private static final long SESSION_TTL_MILLIS = 2 * 3600_000;

    private record Ticket(Long accountId, long expireAt) {
    }

    private final Map<String, Ticket> tickets = new ConcurrentHashMap<>();
    private final Map<String, ProxySession> sessions = new ConcurrentHashMap<>();

    private final WeComAdminAccountService weComAdminAccountService;

    public WeComAdminSsoService(WeComAdminAccountService weComAdminAccountService) {
        this.weComAdminAccountService = weComAdminAccountService;
    }

    /** 为指定账户签发一次性 ticket，返回 ticket 与免登录入口地址。 */
    public Map<String, String> issueTicket(Long accountId) {
        WeComAdminAccount account = weComAdminAccountService.getById(accountId);
        if (account == null) {
            throw new IllegalArgumentException("账户不存在: " + accountId);
        }
        if (!StringUtils.hasText(account.getAdminCookie())) {
            throw new IllegalArgumentException("账户未配置 Cookie，无法免登录跳转");
        }
        cleanupExpired();
        String ticket = newToken();
        tickets.put(ticket, new Ticket(accountId, System.currentTimeMillis() + TICKET_TTL_MILLIS));
        Map<String, String> result = new HashMap<>();
        result.put("ticket", ticket);
        result.put("url", PROXY_BASE + "/entry?ticket=" + ticket);
        return result;
    }

    /** 消费一次性 ticket，签发会话 token（含独立 Cookie Jar，预载账户 Cookie）；ticket 无效或过期返回 null。 */
    public String consumeTicket(String ticket) {
        if (!StringUtils.hasText(ticket)) {
            return null;
        }
        Ticket t = tickets.remove(ticket);
        if (t == null || t.expireAt() < System.currentTimeMillis()) {
            return null;
        }
        String sessionToken = newToken();
        SessionCookieJar jar = new SessionCookieJar();
        // 预载账户存储的 Cookie 到 work 域
        String storedCookie = weComAdminAccountService.getDecryptedCookie(t.accountId());
        if (StringUtils.hasText(storedCookie)) {
            jar.initFromCookieString("work.weixin.qq.com", storedCookie);
        }
        ProxySession session = new ProxySession(t.accountId(),
                System.currentTimeMillis() + SESSION_TTL_MILLIS, jar);
        sessions.put(sessionToken, session);
        return sessionToken;
    }

    /** 校验会话 token，返回会话对象（含 Cookie Jar）；无效或过期返回 null。 */
    public ProxySession resolveSession(String sessionToken) {
        if (!StringUtils.hasText(sessionToken)) {
            return null;
        }
        ProxySession session = sessions.get(sessionToken);
        if (session == null) {
            return null;
        }
        if (session.expireAt() < System.currentTimeMillis()) {
            sessions.remove(sessionToken);
            return null;
        }
        return session;
    }

    private String newToken() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private void cleanupExpired() {
        long now = System.currentTimeMillis();
        tickets.entrySet().removeIf(e -> e.getValue().expireAt() < now);
        sessions.entrySet().removeIf(e -> e.getValue().expireAt() < now);
    }

    /** 代理会话：关联账户 ID 与独立的 Cookie Jar。 */
    public record ProxySession(Long accountId, long expireAt, SessionCookieJar cookieJar) {
    }

    /**
     * 按域隔离的 Cookie Jar。
     * 初始时从账户存储的 Cookie 字符串加载，随后从上游 Set-Cookie 持续更新。
     */
    public static class SessionCookieJar {
        /** domain → (cookieName → cookieValue) */
        private final Map<String, Map<String, String>> jar = new ConcurrentHashMap<>();

        /** 从 Cookie 字符串初始化指定域的 Cookie。 */
        public void initFromCookieString(String domain, String cookieString) {
            if (!StringUtils.hasText(cookieString)) {
                return;
            }
            Map<String, String> cookies = jar.computeIfAbsent(domain, k -> new ConcurrentHashMap<>());
            for (String part : cookieString.split(";")) {
                String trimmed = part.trim();
                if (trimmed.isEmpty()) continue;
                int eq = trimmed.indexOf('=');
                if (eq <= 0) continue;
                cookies.put(trimmed.substring(0, eq).trim(), trimmed.substring(eq + 1).trim());
            }
        }

        /** 从上游 Set-Cookie 头存储 Cookie 到对应域。 */
        public void storeFromSetCookie(String domain, String setCookieHeader) {
            if (!StringUtils.hasText(setCookieHeader)) {
                return;
            }
            String[] parts = setCookieHeader.split(";");
            if (parts.length == 0) return;
            String nameValue = parts[0].trim();
            int eq = nameValue.indexOf('=');
            if (eq <= 0) return;
            String name = nameValue.substring(0, eq).trim();
            String value = nameValue.substring(eq + 1).trim();

            // 检查是否有 Domain 属性，决定存到哪个域
            String targetDomain = domain;
            for (int i = 1; i < parts.length; i++) {
                String attr = parts[i].trim().toLowerCase();
                if (attr.startsWith("domain=")) {
                    String d = attr.substring("domain=".length()).trim();
                    // .work.weixin.qq.com → work.weixin.qq.com
                    if (d.startsWith(".")) d = d.substring(1);
                    targetDomain = d;
                }
                // Max-Age=0 或 Expires 过去 → 删除 Cookie
                if (attr.startsWith("max-age=0") || value.isEmpty()) {
                    Map<String, String> cookies = jar.get(targetDomain);
                    if (cookies != null) {
                        cookies.remove(name);
                    }
                    return;
                }
            }
            jar.computeIfAbsent(targetDomain, k -> new ConcurrentHashMap<>()).put(name, value);
        }

        /** 获取指定域的 Cookie 请求头值。 */
        public String getCookieHeader(String domain) {
            Map<String, String> cookies = jar.get(domain);
            if (cookies == null || cookies.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> entry : cookies.entrySet()) {
                if (sb.length() > 0) sb.append("; ");
                sb.append(entry.getKey()).append("=").append(entry.getValue());
            }
            return sb.toString();
        }
    }
}
