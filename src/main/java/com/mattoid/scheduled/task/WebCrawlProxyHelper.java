package com.mattoid.scheduled.task;

import com.mattoid.scheduled.entity.TaskWebCrawlConfig;
import org.springframework.util.StringUtils;

import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;

/**
 * 网页爬取 HTTP 代理辅助类，支持 jsoup/URLConnection 的代理及认证。
 */
public class WebCrawlProxyHelper {

    private static final ThreadLocal<PasswordAuthentication> PROXY_AUTH = new ThreadLocal<>();

    static {
        Authenticator.setDefault(new ProxyAuthenticator());
    }

    public static boolean isProxyEnabled(TaskWebCrawlConfig config) {
        return config != null
                && Integer.valueOf(1).equals(config.getProxyEnabled())
                && StringUtils.hasText(config.getProxyHost())
                && config.getProxyPort() != null;
    }

    public static Proxy createProxy(TaskWebCrawlConfig config) {
        if (!isProxyEnabled(config)) {
            return null;
        }
        return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(config.getProxyHost(), config.getProxyPort()));
    }

    public static void bindAuth(TaskWebCrawlConfig config) {
        if (!isProxyEnabled(config)) {
            return;
        }
        bindAuth(config.getProxyUsername(), config.getProxyPassword());
    }

    public static void bindAuth(String username, String password) {
        if (StringUtils.hasText(username) && StringUtils.hasText(password)) {
            PROXY_AUTH.set(new PasswordAuthentication(username, password.toCharArray()));
        }
    }

    public static void unbindAuth() {
        PROXY_AUTH.remove();
    }

    private static class ProxyAuthenticator extends Authenticator {
        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
            PasswordAuthentication auth = PROXY_AUTH.get();
            if (auth != null && getRequestorType() == RequestorType.PROXY) {
                return auth;
            }
            return null;
        }
    }
}
