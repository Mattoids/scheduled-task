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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
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
        Path keyPath = writeKeyFile(config.getPrivateKey(), tunnelId);
        try (SSHClient client = createClient()) {
            client.connect(config.getHost(), config.getPort() == null ? 22 : config.getPort());
            authenticate(client, config, keyPath);
            return client.isAuthenticated();
        } finally {
            if (keyPath != null) {
                Files.deleteIfExists(keyPath);
            }
        }
    }

    public SshTunnel createTunnel(DatasourceConfig config) throws Exception {
        SshConfig sshConfig = toSshConfig(config);
        sshConfig.setRemoteHost(config.getHost());
        sshConfig.setRemotePort(config.getPort());
        return createTunnel(sshConfig, buildDatasourceTunnelId(config));
    }

    public SshTunnel createTunnel(SshConfig config, String tunnelId) throws Exception {
        closeTunnel(tunnelId);
        if (config.getJumpHost() != null && !config.getJumpHost().isBlank()) {
            return createDoubleHopTunnel(config, tunnelId);
        }
        return createSingleHopTunnel(config, tunnelId);
    }

    private SshTunnel createSingleHopTunnel(SshConfig config, String tunnelId) throws Exception {
        Path keyPath = writeKeyFile(config.getPrivateKey(), tunnelId);
        SSHClient client = createClient();
        try {
            client.connect(config.getHost(), config.getPort() == null ? 22 : config.getPort());
            authenticate(client, config, keyPath);

            int localPort = config.getLocalPort() != null && config.getLocalPort() > 0
                    ? config.getLocalPort() : findAvailablePort();
            String remoteHost = config.getRemoteHost();
            int remotePort = config.getRemotePort() == null ? 0 : config.getRemotePort();

            ServerSocket serverSocket = new ServerSocket();
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress("127.0.0.1", localPort));

            Parameters params = new Parameters(
                    "127.0.0.1", serverSocket.getLocalPort(), remoteHost, remotePort
            );
            LocalPortForwarder forwarder = new LocalPortForwarder(
                    client.getConnection(), params, serverSocket, LoggerFactory.DEFAULT
            );
            Thread forwarderThread = new Thread(() -> {
                try {
                    forwarder.listen();
                } catch (IOException e) {
                    if (!"Socket closed".equals(e.getMessage())) {
                        log.error("SSH 隧道监听线程异常: {}", tunnelId, e);
                    }
                }
            }, "ssh-tunnel-" + tunnelId);
            forwarderThread.setDaemon(true);
            forwarderThread.start();

            SshTunnel tunnel = new SshTunnel(tunnelId, client, forwarder, forwarderThread,
                    serverSocket.getLocalPort(), "127.0.0.1");
            tunnel.setKeyFilePath(keyPath);
            tunnels.put(tunnelId, tunnel);

            // 等待监听线程进入 accept，避免请求时连接被拒绝
            waitForForwarderStartup(200);

            log.info("SSH tunnel created for {}: 127.0.0.1:{} -> {}:{}",
                    tunnelId, tunnel.getLocalPort(), remoteHost, remotePort);
            return tunnel;
        } catch (Exception e) {
            if (keyPath != null) {
                Files.deleteIfExists(keyPath);
            }
            client.close();
            throw e;
        }
    }

    private SshTunnel createDoubleHopTunnel(SshConfig config, String tunnelId) throws Exception {
        Path jumpKeyPath = writeKeyFile(config.getJumpPrivateKey(), tunnelId + "_jump");
        SSHClient jumpClient = createClient();
        ServerSocket jumpServerSocket = null;
        LocalPortForwarder jumpForwarder = null;
        Thread jumpForwarderThread = null;
        try {
            jumpClient.connect(config.getJumpHost(), config.getJumpPort() == null ? 22 : config.getJumpPort());
            authenticate(jumpClient, buildJumpAuthConfig(config), jumpKeyPath);

            int intermediatePort = findAvailablePort();
            jumpServerSocket = new ServerSocket();
            jumpServerSocket.setReuseAddress(true);
            jumpServerSocket.bind(new InetSocketAddress("127.0.0.1", intermediatePort));

            Parameters jumpParams = new Parameters(
                    "127.0.0.1", jumpServerSocket.getLocalPort(),
                    config.getHost(), config.getPort() == null ? 22 : config.getPort()
            );
            jumpForwarder = new LocalPortForwarder(
                    jumpClient.getConnection(), jumpParams, jumpServerSocket, LoggerFactory.DEFAULT
            );
            final LocalPortForwarder finalJumpForwarder = jumpForwarder;
            jumpForwarderThread = new Thread(() -> {
                try {
                    finalJumpForwarder.listen();
                } catch (IOException e) {
                    if (!"Socket closed".equals(e.getMessage())) {
                        log.error("跳板机端口转发监听线程异常: {}", tunnelId, e);
                    }
                }
            }, "ssh-tunnel-jump-" + tunnelId);
            jumpForwarderThread.setDaemon(true);
            jumpForwarderThread.start();

            // 等待跳板机本地监听线程进入 accept，避免第二跳连接时因线程尚未启动而失败
            waitForForwarderStartup(200);

            Path targetKeyPath = writeKeyFile(config.getPrivateKey(), tunnelId);
            SSHClient targetClient = createClient();
            try {
                targetClient.connect("127.0.0.1", intermediatePort);
                authenticate(targetClient, config, targetKeyPath);

                int localPort = config.getLocalPort() != null && config.getLocalPort() > 0
                        ? config.getLocalPort() : findAvailablePort();
                String remoteHost = config.getRemoteHost();
                int remotePort = config.getRemotePort() == null ? 0 : config.getRemotePort();

                ServerSocket serverSocket = new ServerSocket();
                serverSocket.setReuseAddress(true);
                serverSocket.bind(new InetSocketAddress("127.0.0.1", localPort));

                Parameters params = new Parameters(
                        "127.0.0.1", serverSocket.getLocalPort(), remoteHost, remotePort
                );
                LocalPortForwarder forwarder = new LocalPortForwarder(
                        targetClient.getConnection(), params, serverSocket, LoggerFactory.DEFAULT
                );
                Thread forwarderThread = new Thread(() -> {
                    try {
                        forwarder.listen();
                    } catch (IOException e) {
                        if (!"Socket closed".equals(e.getMessage())) {
                            log.error("SSH 隧道监听线程异常: {}", tunnelId, e);
                        }
                    }
                }, "ssh-tunnel-" + tunnelId);
                forwarderThread.setDaemon(true);
                forwarderThread.start();

                // 等待目标机本地监听线程进入 accept，避免请求时连接被拒绝
                waitForForwarderStartup(200);

                SshTunnel tunnel = new SshTunnel(tunnelId, targetClient, forwarder, forwarderThread,
                        serverSocket.getLocalPort(), "127.0.0.1");
                tunnel.setKeyFilePath(targetKeyPath);
                tunnel.setJumpClient(jumpClient);
                tunnel.setJumpForwarder(jumpForwarder);
                tunnel.setJumpForwarderThread(jumpForwarderThread);
                tunnel.setJumpKeyFilePath(jumpKeyPath);
                tunnels.put(tunnelId, tunnel);
                log.info("SSH double-hop tunnel created for {}: 127.0.0.1:{} -> {}:{} via jump {}",
                        tunnelId, tunnel.getLocalPort(), remoteHost, remotePort, config.getJumpHost());
                return tunnel;
            } catch (Exception e) {
                if (targetKeyPath != null) {
                    Files.deleteIfExists(targetKeyPath);
                }
                targetClient.close();
                throw e;
            }
        } catch (Exception e) {
            if (jumpKeyPath != null) {
                Files.deleteIfExists(jumpKeyPath);
            }
            if (jumpForwarder != null) {
                try {
                    jumpForwarder.close();
                } catch (Exception ignored) {
                }
            }
            if (jumpServerSocket != null && !jumpServerSocket.isClosed()) {
                try {
                    jumpServerSocket.close();
                } catch (Exception ignored) {
                }
            }
            jumpClient.close();
            throw e;
        }
    }

    private SshConfig buildJumpAuthConfig(SshConfig config) {
        SshConfig jumpConfig = new SshConfig();
        jumpConfig.setHost(config.getJumpHost());
        jumpConfig.setPort(config.getJumpPort());
        jumpConfig.setUsername(config.getJumpUsername());
        jumpConfig.setPassword(config.getJumpPassword());
        jumpConfig.setPrivateKey(config.getJumpPrivateKey());
        jumpConfig.setPassphrase(config.getJumpPassphrase());
        jumpConfig.setAuthType(config.getJumpAuthType());
        return jumpConfig;
    }

    private void waitForForwarderStartup(int waitMs) {
        try {
            Thread.sleep(waitMs);
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
        // 未指定时按旧逻辑：有私钥用私钥，否则用密码
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

    private SshConfig toSshConfig(DatasourceConfig config) {
        SshConfig sshConfig = new SshConfig();
        sshConfig.setHost(config.getSshHost());
        sshConfig.setPort(config.getSshPort());
        sshConfig.setUsername(config.getSshUsername());
        sshConfig.setPassword(config.getSshPassword());
        sshConfig.setPrivateKey(config.getSshPrivateKey());
        sshConfig.setPassphrase(config.getSshPassphrase());
        sshConfig.setLocalPort(config.getSshLocalPort());
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
}
