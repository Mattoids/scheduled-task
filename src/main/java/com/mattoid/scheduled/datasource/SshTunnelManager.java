package com.mattoid.scheduled.datasource;

import com.mattoid.scheduled.entity.DatasourceConfig;
import com.mattoid.scheduled.util.CryptoUtil;
import lombok.extern.slf4j.Slf4j;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.LocalPortForwarder;
import net.schmizz.sshj.connection.channel.direct.Parameters;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.keyprovider.FileKeyProvider;
import net.schmizz.sshj.userauth.keyprovider.KeyProvider;
import net.schmizz.sshj.userauth.keyprovider.OpenSSHKeyFile;
import net.schmizz.sshj.userauth.keyprovider.PKCS8KeyFile;
import net.schmizz.sshj.userauth.password.PasswordFinder;
import net.schmizz.sshj.userauth.password.PasswordUtils;
import net.schmizz.sshj.common.LoggerFactory;
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

    private final Map<Long, SshTunnel> tunnels = new ConcurrentHashMap<>();

    public boolean testConnection(DatasourceConfig config) throws Exception {
        Long datasourceId = config.getId() != null ? config.getId() : Long.valueOf(-1);
        closeTunnel(datasourceId);

        Path keyPath = writeKeyFile(config, datasourceId);
        try (SSHClient client = createClient()) {
            client.connect(
                    config.getSshHost(),
                    config.getSshPort() == null ? 22 : config.getSshPort()
            );
            authenticate(client, config, keyPath);
            return client.isAuthenticated();
        } finally {
            if (keyPath != null) {
                Files.deleteIfExists(keyPath);
            }
        }
    }

    public SshTunnel createTunnel(DatasourceConfig config) throws Exception {
        Long datasourceId = config.getId() != null ? config.getId() : Long.valueOf(-1);
        closeTunnel(datasourceId);

        Path keyPath = writeKeyFile(config, datasourceId);
        SSHClient client = createClient();
        try {
            client.connect(
                    config.getSshHost(),
                    config.getSshPort() == null ? 22 : config.getSshPort()
            );
            authenticate(client, config, keyPath);

            int localPort = config.getSshLocalPort() != null && config.getSshLocalPort() > 0
                    ? config.getSshLocalPort() : findAvailablePort();
            String remoteHost = config.getHost();
            int remotePort = config.getPort();

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
                        log.error("SSH 隧道监听线程异常: {}", datasourceId, e);
                    }
                }
            }, "ssh-tunnel-" + datasourceId);
            forwarderThread.setDaemon(true);
            forwarderThread.start();

            SshTunnel tunnel = new SshTunnel(datasourceId, client, forwarder, forwarderThread,
                    serverSocket.getLocalPort(), "127.0.0.1");
            tunnel.setKeyFilePath(keyPath);
            tunnels.put(datasourceId, tunnel);
            log.info("SSH tunnel created for datasource {}: 127.0.0.1:{} -> {}:{}",
                    datasourceId, tunnel.getLocalPort(), remoteHost, remotePort);
            return tunnel;
        } catch (Exception e) {
            if (keyPath != null) {
                Files.deleteIfExists(keyPath);
            }
            client.close();
            throw e;
        }
    }

    public void closeTunnel(Long datasourceId) {
        if (datasourceId == null) {
            return;
        }
        SshTunnel tunnel = tunnels.remove(datasourceId);
        if (tunnel != null) {
            tunnel.disconnect();
            log.info("SSH tunnel closed for datasource {}", datasourceId);
        }
    }

    public SshTunnel getTunnel(Long datasourceId) {
        return tunnels.get(datasourceId);
    }

    private SSHClient createClient() {
        SSHClient client = new SSHClient();
        client.addHostKeyVerifier(new PromiscuousVerifier());
        client.getTransport().setTimeoutMs(30000);
        return client;
    }

    private Path writeKeyFile(DatasourceConfig config, Long datasourceId) throws IOException {
        String privateKey = config.getSshPrivateKey();
        if (privateKey == null || privateKey.isBlank()) {
            return null;
        }
        String decrypted = CryptoUtil.decryptIfNeeded(privateKey);
        String normalized = normalizePrivateKey(decrypted);
        Path keyPath = Files.createTempFile("ssh-key-" + datasourceId, ".key");
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

    private void authenticate(SSHClient client, DatasourceConfig config, Path keyPath) throws Exception {
        if (keyPath != null) {
            KeyProvider keyProvider = createKeyProvider(client, keyPath, config.getSshPassphrase());
            client.authPublickey(config.getSshUsername(), keyProvider);
        } else {
            String sshPassword = config.getSshPassword();
            if (sshPassword != null && !sshPassword.isBlank()) {
                client.authPassword(config.getSshUsername(), CryptoUtil.decryptIfNeeded(sshPassword));
            } else {
                throw new IllegalArgumentException("SSH 密码和私钥至少填写一个");
            }
        }
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
}
