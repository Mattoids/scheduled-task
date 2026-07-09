package com.mattoid.scheduled.datasource;

import com.mattoid.scheduled.entity.DatasourceConfig;
import com.mattoid.scheduled.util.CryptoUtil;
import lombok.extern.slf4j.Slf4j;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.LoggerFactory;
import net.schmizz.sshj.connection.channel.direct.LocalPortForwarder;
import net.schmizz.sshj.connection.channel.direct.Parameters;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.keyprovider.FileKeyProvider;
import net.schmizz.sshj.userauth.keyprovider.KeyProvider;
import net.schmizz.sshj.userauth.keyprovider.OpenSSHKeyFile;
import net.schmizz.sshj.userauth.keyprovider.PKCS8KeyFile;
import net.schmizz.sshj.userauth.password.PasswordFinder;
import net.schmizz.sshj.userauth.password.PasswordUtils;
import com.hierynomus.sshj.userauth.keyprovider.OpenSSHKeyV1KeyFile;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class SshTunnelManager {

    private final Map<String, SshTunnel> tunnels = new ConcurrentHashMap<>();

    public boolean testConnection(DatasourceConfig config) throws Exception {
        return testConnection(toSshConfig(config), buildDatasourceTunnelId(config));
    }

    public boolean testConnection(SshConfig config, String tunnelId) throws Exception {
        closeTunnel(tunnelId);
        ClientChain chain = buildClientChain(config, tunnelId);
        boolean result = chain.serviceClient().isAuthenticated();
        cleanupResources(chain.intermediateResources());
        disconnectClient(chain.serviceClient());
        deleteKeyFile(chain.serviceKeyPath());
        return result;
    }

    public SshTunnel createTunnel(DatasourceConfig config) throws Exception {
        SshConfig sshConfig = toSshConfig(config);
        sshConfig.setRemoteHost(config.getHost());
        sshConfig.setRemotePort(config.getPort());
        return createTunnel(sshConfig, buildDatasourceTunnelId(config));
    }

    public SshTunnel createTunnel(SshConfig config, String tunnelId) throws Exception {
        closeTunnel(tunnelId);

        String remoteHost = config.getRemoteHost();
        int remotePort = resolveRemotePort(config.getRemotePort());
        if (!StringUtils.hasText(remoteHost)) {
            throw new IllegalArgumentException("SSH 隧道目标主机未配置");
        }

        int localPort = config.getLocalPort() != null && config.getLocalPort() > 0
                ? config.getLocalPort() : findAvailablePort();

        ClientChain chain = buildClientChain(config, tunnelId);
        SSHClient serviceClient = chain.serviceClient();
        Path serviceKeyPath = chain.serviceKeyPath();
        List<SshTunnel.HopResource> intermediateResources = chain.intermediateResources();

        try {
            ServerSocket serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress("127.0.0.1", localPort));

            Parameters params = new Parameters(
                    "127.0.0.1", serverSocket.getLocalPort(), remoteHost, remotePort
            );
            LocalPortForwarder forwarder = new LocalPortForwarder(
                    serviceClient.getConnection(), params, serverSocket, LoggerFactory.DEFAULT
            );
            Thread forwarderThread = startForwarder(forwarder, tunnelId);
            waitForForwarderStartup(200);

            SshTunnel tunnel = new SshTunnel(tunnelId, serviceClient, forwarder, forwarderThread,
                    serverSocket.getLocalPort(), "127.0.0.1");
            tunnel.setKeyFilePath(serviceKeyPath);
            tunnel.getIntermediateHops().addAll(intermediateResources);
            tunnels.put(tunnelId, tunnel);

            List<SshHopConfig> connectionHops = toConnectionOrder(config.getHops());
            log.info("SSH {} tunnel created for {}: 127.0.0.1:{} -> {}:{} via {}",
                    connectionHops.isEmpty() ? "single-hop" : "multi-hop",
                    tunnelId, tunnel.getLocalPort(), remoteHost, remotePort,
                    connectionHops.stream().map(SshHopConfig::getHost).toList());
            return tunnel;
        } catch (Exception e) {
            disconnectClient(serviceClient);
            deleteKeyFile(serviceKeyPath);
            cleanupResources(intermediateResources);
            throw e;
        }
    }

    /**
     * 建立 SSH 客户端链路。返回的客户端已连接到最终的服务所在机器，
     * 中间节点资源按从请求侧到服务侧的顺序保存。
     */
    private ClientChain buildClientChain(SshConfig config, String tunnelId) throws Exception {
        List<SshHopConfig> connectionHops = toConnectionOrder(config.getHops());
        List<SshTunnel.HopResource> intermediateResources = new ArrayList<>();

        SSHClient currentClient;
        Path currentKeyPath;

        if (connectionHops.isEmpty()) {
            currentKeyPath = writeKeyFile(config.getPrivateKey(), tunnelId);
            currentClient = createClient();
            int sshPort = config.getPort() == null ? 22 : config.getPort();
            log.info("SSH 直接连接目标服务器 {}:{}", config.getHost(), sshPort);
            connectClient(currentClient, config.getHost(), sshPort,
                    "SSH 直接连接目标服务器失败: " + config.getHost() + ":" + sshPort);
            authenticate(currentClient, config, currentKeyPath);
        } else {
            SshHopConfig firstHop = connectionHops.get(0);
            currentKeyPath = writeKeyFile(firstHop.getPrivateKey(), tunnelId + "_hop_0");
            currentClient = createClient();
            int firstHopPort = firstHop.getPort() == null ? 22 : firstHop.getPort();
            log.info("SSH 多跳链路：第 1 跳 {}:{}", firstHop.getHost(), firstHopPort);
            connectClient(currentClient, firstHop.getHost(), firstHopPort,
                    "SSH 第 1 跳连接失败: " + firstHop.getHost() + ":" + firstHopPort);
            authenticate(currentClient, hopToSshConfig(firstHop), currentKeyPath);

            for (int i = 1; i < connectionHops.size(); i++) {
                SshHopConfig nextHop = connectionHops.get(i);
                int intermediatePort = findAvailablePort();

                ServerSocket serverSocket = new ServerSocket();
                serverSocket.setReuseAddress(true);
                serverSocket.bind(new InetSocketAddress("127.0.0.1", intermediatePort));

                int nextHopPort = nextHop.getPort() == null ? 22 : nextHop.getPort();
                log.info("SSH 多跳链路：经本地 127.0.0.1:{} 转发到第 {} 跳 {}:{}",
                        intermediatePort, i + 1, nextHop.getHost(), nextHopPort);
                Parameters params = new Parameters(
                        "127.0.0.1", serverSocket.getLocalPort(),
                        nextHop.getHost(), nextHopPort
                );
                LocalPortForwarder forwarder = new LocalPortForwarder(
                        currentClient.getConnection(), params, serverSocket, LoggerFactory.DEFAULT
                );
                Thread forwarderThread = startForwarder(forwarder, tunnelId + "_hop_" + (i - 1));
                waitForForwarderStartup(200);

                intermediateResources.add(new SshTunnel.HopResource(
                        currentClient, forwarder, forwarderThread, serverSocket, currentKeyPath
                ));

                currentKeyPath = writeKeyFile(nextHop.getPrivateKey(), tunnelId + "_hop_" + i);
                SSHClient nextClient = createClient();
                connectClient(nextClient, "127.0.0.1", intermediatePort,
                        "SSH 经本地端口 127.0.0.1:" + intermediatePort + " 转发连接失败");
                authenticate(nextClient, hopToSshConfig(nextHop), currentKeyPath);

                currentClient = nextClient;
            }

            int serviceIntermediatePort = findAvailablePort();
            ServerSocket serviceServerSocket = new ServerSocket();
            serviceServerSocket.setReuseAddress(true);
            serviceServerSocket.bind(new InetSocketAddress("127.0.0.1", serviceIntermediatePort));

            int serviceSshPort = config.getPort() == null ? 22 : config.getPort();
            log.info("SSH 多跳链路：经本地 127.0.0.1:{} 转发到目标服务器 {}:{}",
                    serviceIntermediatePort, config.getHost(), serviceSshPort);
            Parameters serviceParams = new Parameters(
                    "127.0.0.1", serviceServerSocket.getLocalPort(),
                    config.getHost(), serviceSshPort
            );
            LocalPortForwarder serviceForwarder = new LocalPortForwarder(
                    currentClient.getConnection(), serviceParams, serviceServerSocket, LoggerFactory.DEFAULT
            );
            Thread serviceForwarderThread = startForwarder(serviceForwarder, tunnelId + "_service");
            waitForForwarderStartup(200);

            intermediateResources.add(new SshTunnel.HopResource(
                    currentClient, serviceForwarder, serviceForwarderThread, serviceServerSocket, currentKeyPath
            ));

            currentKeyPath = writeKeyFile(config.getPrivateKey(), tunnelId);
            currentClient = createClient();
            connectClient(currentClient, "127.0.0.1", serviceIntermediatePort,
                    "SSH 经本地端口 127.0.0.1:" + serviceIntermediatePort + " 转发连接失败");
            authenticate(currentClient, config, currentKeyPath);
        }

        return new ClientChain(currentClient, currentKeyPath, intermediateResources);
    }

    private List<SshHopConfig> toConnectionOrder(List<SshHopConfig> hops) {
        if (hops == null || hops.isEmpty()) {
            return Collections.emptyList();
        }
        List<SshHopConfig> result = new ArrayList<>(hops);
        Collections.reverse(result);
        return result;
    }

    private SshConfig hopToSshConfig(SshHopConfig hop) {
        SshConfig config = new SshConfig();
        config.setHost(hop.getHost());
        config.setPort(hop.getPort());
        config.setUsername(hop.getUsername());
        config.setPassword(hop.getPassword());
        config.setPrivateKey(hop.getPrivateKey());
        config.setPassphrase(hop.getPassphrase());
        config.setAuthType(hop.getAuthType());
        return config;
    }

    private Thread startForwarder(LocalPortForwarder forwarder, String name) {
        Thread thread = new Thread(() -> {
            try {
                forwarder.listen();
            } catch (IOException e) {
                if (!"Socket closed".equals(e.getMessage())) {
                    log.error("SSH 端口转发监听线程异常: {}", name, e);
                }
            }
        }, "ssh-tunnel-" + name);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private void cleanupResources(List<SshTunnel.HopResource> resources) {
        for (int i = resources.size() - 1; i >= 0; i--) {
            SshTunnel.HopResource hop = resources.get(i);
            closeForwarder(hop.getForwarder());
            interruptThread(hop.getForwarderThread());
            sleepQuietly(100);
            closeServerSocket(hop.getServerSocket());
            disconnectClient(hop.getClient());
            deleteKeyFile(hop.getKeyFilePath());
        }
    }

    private void closeForwarder(LocalPortForwarder forwarder) {
        if (forwarder != null) {
            try {
                forwarder.close();
            } catch (Exception e) {
                log.warn("关闭 SSH 端口转发失败", e);
            }
        }
    }

    private void interruptThread(Thread thread) {
        if (thread != null) {
            thread.interrupt();
        }
    }

    private void closeServerSocket(ServerSocket serverSocket) {
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (Exception e) {
                log.warn("关闭 SSH 隧道本地监听端口失败", e);
            }
        }
    }

    private void disconnectClient(SSHClient client) {
        if (client != null && client.isConnected()) {
            try {
                client.disconnect();
            } catch (Exception e) {
                log.warn("断开 SSH 连接失败", e);
            }
        }
    }

    private void deleteKeyFile(Path keyFilePath) {
        if (keyFilePath != null) {
            try {
                Files.deleteIfExists(keyFilePath);
            } catch (Exception e) {
                log.warn("删除临时 SSH 密钥文件失败: {}", keyFilePath, e);
            }
        }
    }

    private void waitForForwarderStartup(int waitMs) {
        try {
            Thread.sleep(waitMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void sleepQuietly(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public void closeTunnel(Long datasourceId) {
        closeTunnel(buildDatasourceTunnelId(datasourceId));
    }

    public void closeTunnel(String tunnelId) {
        if (tunnelId == null) {
            return;
        }
        SshTunnel tunnel = tunnels.remove(tunnelId);
        if (tunnel != null) {
            tunnel.disconnect();
            log.info("SSH tunnel closed for {}", tunnelId);
        }
    }

    public SshTunnel getTunnel(Long datasourceId) {
        return tunnels.get(buildDatasourceTunnelId(datasourceId));
    }

    public SshTunnel getTunnel(String tunnelId) {
        return tunnels.get(tunnelId);
    }

    private SSHClient createClient() {
        SSHClient client = new SSHClient();
        client.addHostKeyVerifier(new PromiscuousVerifier());
        client.getTransport().setTimeoutMs(30000);
        return client;
    }

    private void connectClient(SSHClient client, String host, int port, String errorHint) throws IOException {
        try {
            client.connect(host, port);
        } catch (IOException e) {
            String msg = e.getMessage();
            if (msg != null && (msg.contains("Operation timed out") || msg.contains("Connection timed out") || msg.contains("Connection refused"))) {
                throw new IOException(errorHint + " - " + msg, e);
            }
            throw e;
        }
    }

    private Path writeKeyFile(String privateKey, String tunnelId) throws IOException {
        if (privateKey == null || privateKey.isBlank()) {
            return null;
        }
        String decrypted = CryptoUtil.decryptIfNeeded(privateKey);
        String normalized = normalizePrivateKey(decrypted);
        Path keyPath = Files.createTempFile("ssh-key-" + sanitizeTunnelId(tunnelId), ".key");
        Files.writeString(keyPath, normalized);
        return keyPath;
    }

    private String normalizePrivateKey(String privateKey) {
        return privateKey
                .replace("\\n", "\n")
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .trim();
    }

    private void authenticate(SSHClient client, SshConfig config, Path keyPath) throws Exception {
        Boolean useKey = resolveUseKey(config, keyPath);
        if (Boolean.TRUE.equals(useKey)) {
            KeyProvider keyProvider = createKeyProvider(client, keyPath, config.getPassphrase());
            client.authPublickey(config.getUsername(), keyProvider);
        } else {
            String sshPassword = config.getPassword();
            if (sshPassword != null && !sshPassword.isBlank()) {
                client.authPassword(config.getUsername(), CryptoUtil.decryptIfNeeded(sshPassword));
            } else {
                throw new IllegalArgumentException("SSH 密码和私钥至少填写一个");
            }
        }
    }

    private Boolean resolveUseKey(SshConfig config, Path keyPath) {
        if ("KEY".equalsIgnoreCase(config.getAuthType())) {
            return Boolean.TRUE;
        }
        if ("PASSWORD".equalsIgnoreCase(config.getAuthType())) {
            return Boolean.FALSE;
        }
        return keyPath != null ? Boolean.TRUE : Boolean.FALSE;
    }

    private KeyProvider createKeyProvider(SSHClient client, Path keyPath, String passphrase) throws IOException {
        String decryptedPassphrase = passphrase != null && !passphrase.isBlank()
                ? CryptoUtil.decryptIfNeeded(passphrase) : null;
        try {
            if (decryptedPassphrase != null) {
                return client.loadKeys(keyPath.toString(), decryptedPassphrase);
            } else {
                return client.loadKeys(keyPath.toString());
            }
        } catch (IOException e) {
            String msg = e.getMessage();
            if (msg != null && (msg.contains("Unknown key file") || msg.contains("No provider available"))) {
                log.warn("sshj 自动识别密钥格式失败，尝试按文件头显式解析: {}", keyPath);
                return createExplicitKeyProvider(keyPath, decryptedPassphrase);
            }
            throw e;
        }
    }

    private KeyProvider createExplicitKeyProvider(Path keyPath, String passphrase) throws IOException {
        String firstLine;
        try (BufferedReader reader = Files.newBufferedReader(keyPath)) {
            firstLine = reader.readLine();
        }
        if (firstLine == null || firstLine.isBlank()) {
            throw new IOException("密钥文件为空");
        }

        PasswordFinder pwdf = passphrase != null && !passphrase.isBlank()
                ? PasswordUtils.createOneOff(passphrase.toCharArray())
                : null;

        FileKeyProvider provider;
        if (firstLine.contains("OPENSSH PRIVATE KEY")) {
            provider = new OpenSSHKeyV1KeyFile();
        } else if (firstLine.contains("-----BEGIN PRIVATE KEY-----") || firstLine.contains("-----BEGIN ENCRYPTED PRIVATE KEY-----")) {
            provider = new PKCS8KeyFile();
        } else if (firstLine.contains("PRIVATE KEY")) {
            provider = new OpenSSHKeyFile();
        } else {
            throw new IOException("不支持的 SSH 私钥格式: " + firstLine);
        }

        File keyFile = keyPath.toFile();
        if (pwdf != null) {
            provider.init(keyFile, pwdf);
        } else {
            provider.init(keyFile);
        }
        return provider;
    }

    private int findAvailablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private int resolveRemotePort(Integer remotePort) {
        if (remotePort != null && remotePort > 0) {
            return remotePort;
        }
        throw new IllegalArgumentException("SSH 隧道目标端口未配置或无效: " + remotePort);
    }

    private SshConfig toSshConfig(DatasourceConfig config) {
        SshConfig sshConfig = new SshConfig();
        sshConfig.setHost(config.getSshHost());
        sshConfig.setPort(config.getSshPort());
        sshConfig.setUsername(config.getSshUsername());
        sshConfig.setPassword(config.getSshPassword());
        sshConfig.setPrivateKey(config.getSshPrivateKey());
        sshConfig.setPassphrase(config.getSshPassphrase());
        sshConfig.setLocalPort(config.getSshLocalPort());
        sshConfig.setAuthType(config.getSshAuthType());
        return sshConfig;
    }

    private String buildDatasourceTunnelId(DatasourceConfig config) {
        return buildDatasourceTunnelId(config.getId() != null ? config.getId() : -1L);
    }

    private String buildDatasourceTunnelId(Long datasourceId) {
        return "datasource_" + datasourceId;
    }

    private String sanitizeTunnelId(String tunnelId) {
        if (tunnelId == null) {
            return "unknown";
        }
        return tunnelId.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private record ClientChain(SSHClient serviceClient, Path serviceKeyPath,
                               List<SshTunnel.HopResource> intermediateResources) {
    }
}
