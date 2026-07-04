package com.mattoid.scheduled.service.wecom;

import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.util.http.apache.ApacheHttpClientBuilder;
import me.chanjar.weixin.common.util.http.apache.DefaultApacheHttpClientBuilder;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;

import java.io.IOException;

/**
 * 企业微信应用 API 请求日志增强的 Apache HttpClient Builder。
 * <p>
 * 在默认构建器基础上包装一个 {@link CloseableHttpClient}，记录每次 outgoing 请求的
 * 方法、URI、响应状态码及耗时，便于排查企业微信接口问题。
 */
@Slf4j
public class WeComHttpClientLoggingBuilder implements ApacheHttpClientBuilder {

    private final DefaultApacheHttpClientBuilder delegate = DefaultApacheHttpClientBuilder.get();

    @Override
    public CloseableHttpClient build() {
        return new LoggingCloseableHttpClient(delegate.build());
    }

    @Override
    public ApacheHttpClientBuilder httpProxyHost(String httpProxyHost) {
        delegate.httpProxyHost(httpProxyHost);
        return this;
    }

    @Override
    public ApacheHttpClientBuilder httpProxyPort(int httpProxyPort) {
        delegate.httpProxyPort(httpProxyPort);
        return this;
    }

    @Override
    public ApacheHttpClientBuilder httpProxyUsername(String httpProxyUsername) {
        delegate.httpProxyUsername(httpProxyUsername);
        return this;
    }

    @Override
    public ApacheHttpClientBuilder httpProxyPassword(String httpProxyPassword) {
        delegate.httpProxyPassword(httpProxyPassword);
        return this;
    }

    @Override
    public ApacheHttpClientBuilder httpRequestRetryHandler(HttpRequestRetryHandler handler) {
        delegate.httpRequestRetryHandler(handler);
        return this;
    }

    @Override
    public ApacheHttpClientBuilder keepAliveStrategy(ConnectionKeepAliveStrategy keepAliveStrategy) {
        delegate.keepAliveStrategy(keepAliveStrategy);
        return this;
    }

    @Override
    public ApacheHttpClientBuilder sslConnectionSocketFactory(SSLConnectionSocketFactory sslConnectionSocketFactory) {
        delegate.sslConnectionSocketFactory(sslConnectionSocketFactory);
        return this;
    }

    private static class LoggingCloseableHttpClient extends CloseableHttpClient {

        private final CloseableHttpClient delegate;

        LoggingCloseableHttpClient(CloseableHttpClient delegate) {
            this.delegate = delegate;
        }

        @Override
        protected CloseableHttpResponse doExecute(HttpHost target, HttpRequest request, HttpContext context)
                throws IOException, ClientProtocolException {
            long start = System.currentTimeMillis();
            String method = request.getRequestLine().getMethod();
            String uri = sanitizeUri(request.getRequestLine().getUri());

            if (request instanceof HttpEntityEnclosingRequest enclosing) {
                HttpEntity entity = enclosing.getEntity();
                log.info("企业微信应用 API 请求: method={}, uri={}, entityContentType={}, entityLength={}",
                        method,
                        uri,
                        entity != null && entity.getContentType() != null ? entity.getContentType().getValue() : null,
                        entity != null ? entity.getContentLength() : 0);
            } else {
                log.info("企业微信应用 API 请求: method={}, uri={}", method, uri);
            }

            try {
                CloseableHttpResponse response = delegate.execute(target, request, context);
                long duration = System.currentTimeMillis() - start;
                HttpEntity entity = response.getEntity();
                log.info("企业微信应用 API 响应: status={}, duration={}ms, contentType={}, contentLength={}",
                        response.getStatusLine().getStatusCode(),
                        duration,
                        entity != null && entity.getContentType() != null ? entity.getContentType().getValue() : null,
                        entity != null ? entity.getContentLength() : 0);
                return response;
            } catch (Exception e) {
                log.error("企业微信应用 API 请求失败: method={}, uri={}, duration={}ms",
                        method, uri, System.currentTimeMillis() - start, e);
                throw e;
            }
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        @Override
        public HttpParams getParams() {
            return delegate.getParams();
        }

        @Override
        public ClientConnectionManager getConnectionManager() {
            return delegate.getConnectionManager();
        }

        private static String sanitizeUri(String uri) {
            if (uri == null) {
                return null;
            }
            return uri.replaceAll("(?i)(corpsecret|access_token)=[^&]*", "$1=***");
        }
    }
}
