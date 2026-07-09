package com.mattoid.scheduled.datasource;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.channel.direct.LocalPortForwarder;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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

    /**
     * 多跳链路中的中间节点资源（从请求侧到服务侧排序）。
     * 为空时表示直连服务所在机器。
     */
    private List<HopResource> intermediateHops = new ArrayList<>();

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
        if (client == null || !client.isConnected() || !client.isAuthenticated()) {
            return false;
        }
        for (HopResource hop : intermediateHops) {
            if (hop.getClient() == null || !hop.getClient().isConnected()
                    || !hop.getClient().isAuthenticated()) {
                return false;
            }
        }
        return true;
    }

    public void disconnect() {
        closeForwarder(forwarder);
        interruptThread(forwarderThread);
        sleepQuietly(100);
        disconnectClient(client);

        for (int i = intermediateHops.size() - 1; i >= 0; i--) {
            HopResource hop = intermediateHops.get(i);
            closeForwarder(hop.getForwarder());
            interruptThread(hop.getForwarderThread());
            sleepQuietly(100);
            closeServerSocket(hop.getServerSocket());
            disconnectClient(hop.getClient());
            deleteKeyFile(hop.getKeyFilePath());
        }

        deleteKeyFile(keyFilePath);
    }

    private void closeForwarder(LocalPortForwarder forwarder) {
        if (forwarder != null) {
            try {
                forwarder.close();
            } catch (Exception e) {
                log.warn("关闭 SSH 端口转发失败: {}", tunnelId, e);
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
                log.warn("关闭 SSH 隧道本地监听端口失败: {}", tunnelId, e);
            }
        }
    }

    private void disconnectClient(SSHClient client) {
        if (client != null && client.isConnected()) {
            try {
                client.disconnect();
            } catch (Exception e) {
                log.warn("断开 SSH 连接失败: {}", tunnelId, e);
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

    private void sleepQuietly(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Data
    public static class HopResource {
        private final SSHClient client;
        private final LocalPortForwarder forwarder;
        private final Thread forwarderThread;
        private final ServerSocket serverSocket;
        private final Path keyFilePath;
    }
}
