package com.mattoid.scheduled.task;

import com.mattoid.scheduled.datasource.SshConfig;
import com.mattoid.scheduled.datasource.SshTunnel;
import com.mattoid.scheduled.datasource.SshTunnelManager;
import com.mattoid.scheduled.entity.TaskWebCrawlConfig;
import com.mattoid.scheduled.service.TaskWebCrawlConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.MalformedURLException;
import java.net.URL;

@Slf4j
@Service
public class WebCrawlSshTunnelService {

    private static final String TUNNEL_ID_PREFIX = "crawl_persistent_";

    private final TaskWebCrawlConfigService taskWebCrawlConfigService;
    private final WebCrawlExecutor webCrawlExecutor;
    private final SshTunnelManager sshTunnelManager;

    public WebCrawlSshTunnelService(TaskWebCrawlConfigService taskWebCrawlConfigService,
                                    WebCrawlExecutor webCrawlExecutor,
                                    SshTunnelManager sshTunnelManager) {
        this.taskWebCrawlConfigService = taskWebCrawlConfigService;
        this.webCrawlExecutor = webCrawlExecutor;
        this.sshTunnelManager = sshTunnelManager;
    }

    public CrawlSshTunnelInfo openTunnel(Long configId) throws Exception {
        TaskWebCrawlConfig config = taskWebCrawlConfigService.getDecryptedById(configId);
        if (config == null) {
            throw new IllegalArgumentException("爬取配置不存在");
        }
        if (!Integer.valueOf(1).equals(config.getSshEnabled())) {
            throw new IllegalArgumentException("该爬取配置未启用 SSH 隧道");
        }

        SshConfig sshConfig = webCrawlExecutor.buildSshConfig(config);
        String tunnelId = buildTunnelId(configId);
        SshTunnel tunnel = sshTunnelManager.createTunnel(sshConfig, tunnelId);

        String localUrl = buildLocalUrl(config.getRequestUrl(), tunnel.getLocalPort());
        log.info("网页爬取 SSH 隧道已开启: configId={}, tunnelId={}, localPort={}, localUrl={}",
                configId, tunnelId, tunnel.getLocalPort(), localUrl);
        return new CrawlSshTunnelInfo(true, tunnel.getLocalPort(), localUrl,
                "SSH 隧道已开启: 127.0.0.1:" + tunnel.getLocalPort());
    }

    public boolean closeTunnel(Long configId) {
        String tunnelId = buildTunnelId(configId);
        sshTunnelManager.closeTunnel(tunnelId);
        log.info("网页爬取 SSH 隧道已关闭: configId={}, tunnelId={}", configId, tunnelId);
        return true;
    }

    public CrawlSshTunnelInfo getStatus(Long configId) {
        String tunnelId = buildTunnelId(configId);
        SshTunnel tunnel = sshTunnelManager.getTunnel(tunnelId);
        if (tunnel != null && tunnel.isConnected()) {
            return new CrawlSshTunnelInfo(true, tunnel.getLocalPort(),
                    buildLocalUrlForPort(tunnel.getLocalPort()),
                    "SSH 隧道运行中: 127.0.0.1:" + tunnel.getLocalPort());
        }
        return new CrawlSshTunnelInfo(false, null, null, "SSH 隧道未开启");
    }

    private String buildTunnelId(Long configId) {
        return TUNNEL_ID_PREFIX + configId;
    }

    private String buildLocalUrl(String requestUrl, int localPort) {
        if (!StringUtils.hasText(requestUrl)) {
            return buildLocalUrlForPort(localPort);
        }
        try {
            URL parsed = new URL(requestUrl);
            return new URL(parsed.getProtocol(), "127.0.0.1", localPort, parsed.getFile()).toString();
        } catch (MalformedURLException e) {
            return buildLocalUrlForPort(localPort);
        }
    }

    private String buildLocalUrlForPort(int localPort) {
        return "http://127.0.0.1:" + localPort;
    }

    public record CrawlSshTunnelInfo(boolean connected, Integer localPort, String localUrl, String message) {
    }
}
