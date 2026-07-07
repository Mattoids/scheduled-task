package com.mattoid.scheduled.datasource;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.LocalPortForwarder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Data
public class SshTunnel {

    private final String tunnelId;
    private final SSHClient client;
    private final LocalPortForwarder forwarder;
    private final Thread forwarderThread;
    private final int localPort;
    private final String localHost;
    private Path keyFilePath;

    public SshTunnel(String tunnelId, SSHClient client, LocalPortForwarder forwarder,
                     Thread forwarderThread, int localPort, String localHost) {
        this.tunnelId = tunnelId;
        this.client = client;
        this.forwarder = forwarder;
        this.forwarderThread = forwarderThread;
        this.localPort = localPort;
        this.localHost = localHost;
    }

    public boolean isConnected() {
        return client != null && client.isConnected() && client.isAuthenticated();
    }

    public void disconnect() {
        if (forwarder != null) {
            try {
                forwarder.close();
            } catch (Exception e) {
                log.warn("关闭 SSH 端口转发失败: {}", tunnelId, e);
            }
        }
        if (forwarderThread != null) {
            forwarderThread.interrupt();
        }
        if (client != null && client.isConnected()) {
            try {
                client.disconnect();
            } catch (Exception e) {
                log.warn("断开 SSH 连接失败: {}", tunnelId, e);
            }
        }
        if (keyFilePath != null) {
            try {
                Files.deleteIfExists(keyFilePath);
            } catch (Exception e) {
                log.warn("删除临时 SSH 密钥文件失败: {}", keyFilePath, e);
            }
        }
    }
}
