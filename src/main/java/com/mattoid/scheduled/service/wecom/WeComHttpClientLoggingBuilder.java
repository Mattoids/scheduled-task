package com.mattoid.scheduled.service.wecom;

import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.util.http.apache.ApacheHttpClientBuilder;
import me.chanjar.weixin.common.util.http.apache.DefaultApacheHttpClientBuilder;
import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

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

        private static final String DEFAULT_USER_AGENT = "ScheduledTask/1.0";

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

            if (!request.containsHeader("User-Agent")) {
                request.setHeader("User-Agent", DEFAULT_USER_AGENT);
            }

            // 为上传文件等耗时操作设置更长的超时
            if (request instanceof HttpRequestBase requestBase) {
                RequestConfig current = requestBase.getConfig();
                RequestConfig.Builder configBuilder = current != null ? RequestConfig.copy(current) : RequestConfig.custom();
                RequestConfig requestConfig = configBuilder
                        .setConnectTimeout(10000)
                        .setSocketTimeout(60000)
                        .setConnectionRequestTimeout(10000)
                        .build();
                requestBase.setConfig(requestConfig);
            }

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

            CloseableHttpResponse response;
            try {
                response = delegate.execute(target, request, context);
            } catch (Exception e) {
                log.error("企业微信应用 API 请求失败: method={}, uri={}, duration={}ms",
                        method, uri, System.currentTimeMillis() - start, e);
                throw e;
            }

            long duration = System.currentTimeMillis() - start;
            int status = response.getStatusLine().getStatusCode();
            HttpEntity originalEntity = response.getEntity();
            BufferedHttpEntity bufferedEntity = originalEntity == null ? null : new BufferedHttpEntity(originalEntity);

            if (bufferedEntity != null) {
                log.info("企业微信应用 API 响应: status={}, duration={}ms, contentType={}, contentLength={}",
                        status,
                        duration,
                        bufferedEntity.getContentType() != null ? bufferedEntity.getContentType().getValue() : null,
                        bufferedEntity.getContentLength());
                if (status >= 400) {
                    String body = EntityUtils.toString(bufferedEntity, StandardCharsets.UTF_8);
                    log.error("企业微信应用 API 错误响应体: status={}, body={}", status,
                            body.length() > 2000 ? body.substring(0, 2000) + "..." : body);
                }
            } else {
                log.info("企业微信应用 API 响应: status={}, duration={}ms, contentType=null, contentLength=0",
                        status, duration);
            }

            return new BufferedCloseableHttpResponse(response, bufferedEntity);
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

    /**
     * 将响应实体缓冲到内存的包装器，便于在记录错误响应体的同时不破坏上游调用方对响应的读取。
     */
    private static class BufferedCloseableHttpResponse implements CloseableHttpResponse {

        private final CloseableHttpResponse delegate;
        private final HttpEntity bufferedEntity;

        BufferedCloseableHttpResponse(CloseableHttpResponse delegate, HttpEntity bufferedEntity) {
            this.delegate = delegate;
            this.bufferedEntity = bufferedEntity;
        }

        @Override
        public HttpEntity getEntity() {
            return bufferedEntity;
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        @Override
        public StatusLine getStatusLine() {
            return delegate.getStatusLine();
        }

        @Override
        public void setStatusLine(StatusLine statusline) {
            delegate.setStatusLine(statusline);
        }

        @Override
        public void setStatusLine(ProtocolVersion ver, int code) {
            delegate.setStatusLine(ver, code);
        }

        @Override
        public void setStatusLine(ProtocolVersion ver, int code, String reason) {
            delegate.setStatusLine(ver, code, reason);
        }

        @Override
        public void setStatusCode(int code) throws IllegalStateException {
            delegate.setStatusCode(code);
        }

        @Override
        public void setReasonPhrase(String reason) throws IllegalStateException {
            delegate.setReasonPhrase(reason);
        }

        @Override
        public void setLocale(Locale locale) {
            delegate.setLocale(locale);
        }

        @Override
        public Locale getLocale() {
            return delegate.getLocale();
        }

        @Override
        public void setEntity(HttpEntity entity) {
            delegate.setEntity(entity);
        }

        @Override
        public ProtocolVersion getProtocolVersion() {
            return delegate.getProtocolVersion();
        }

        @Override
        public boolean containsHeader(String name) {
            return delegate.containsHeader(name);
        }

        @Override
        public Header[] getHeaders(String name) {
            return delegate.getHeaders(name);
        }

        @Override
        public Header getFirstHeader(String name) {
            return delegate.getFirstHeader(name);
        }

        @Override
        public Header getLastHeader(String name) {
            return delegate.getLastHeader(name);
        }

        @Override
        public Header[] getAllHeaders() {
            return delegate.getAllHeaders();
        }

        @Override
        public void addHeader(Header header) {
            delegate.addHeader(header);
        }

        @Override
        public void addHeader(String name, String value) {
            delegate.addHeader(name, value);
        }

        @Override
        public void removeHeader(Header header) {
            delegate.removeHeader(header);
        }

        @Override
        public void setHeader(Header header) {
            delegate.setHeader(header);
        }

        @Override
        public void setHeader(String name, String value) {
            delegate.setHeader(name, value);
        }

        @Override
        public void setHeaders(Header[] headers) {
            delegate.setHeaders(headers);
        }

        @Override
        public void removeHeaders(String name) {
            delegate.removeHeaders(name);
        }

        @Override
        public HeaderIterator headerIterator() {
            return delegate.headerIterator();
        }

        @Override
        public HeaderIterator headerIterator(String name) {
            return delegate.headerIterator(name);
        }

        @Override
        public HttpParams getParams() {
            return delegate.getParams();
        }

        @Override
        public void setParams(HttpParams params) {
            delegate.setParams(params);
        }
    }
}
