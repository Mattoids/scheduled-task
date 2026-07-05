package com.mattoid.scheduled.service.wecom;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mattoid.scheduled.entity.NotificationConfig;
import com.mattoid.scheduled.entity.WeComIntelligentBotConfig;
import com.mattoid.scheduled.mapper.NotificationConfigMapper;
import com.mattoid.scheduled.util.CryptoUtil;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.cp.api.WxCpService;
import me.chanjar.weixin.cp.api.impl.WxCpServiceImpl;
import me.chanjar.weixin.cp.bean.message.WxCpMessage;
import me.chanjar.weixin.cp.config.impl.WxCpDefaultConfigImpl;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.io.File;
import java.io.FileInputStream;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class WeComIntelligentBotClient {

    private static final String WSS_URL_TEMPLATE = "wss://qyapi.weixin.qq.com/cgi-bin/webhook/easy_robots?wskey=%s";
    private static final int RECONNECT_DELAY_SECONDS = 5;
    private static final int RECONNECT_MAX_DELAY_SECONDS = 60;

    private final NotificationConfigMapper notificationConfigMapper;
    private final WeComCommandHandler weComCommandHandler;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebSocketClient webSocketClient;
    private final Map<Long, BotConnection> connections = new ConcurrentHashMap<>();

    public WeComIntelligentBotClient(NotificationConfigMapper notificationConfigMapper,
                                     WeComCommandHandler weComCommandHandler) {
        this.notificationConfigMapper = notificationConfigMapper;
        this.weComCommandHandler = weComCommandHandler;
        this.webSocketClient = new StandardWebSocketClient();
    }

    /**
     * 通过通知配置 ID 连接智能机器人长链
     */
    public void connect(Long configId) {
        BotConnection existing = connections.get(configId);
        if (existing != null && existing.isConnected()) {
            log.info("智能机器人连接已存在: configId={}", configId);
            return;
        }

        try {
            WeComIntelligentBotConfig config = loadConfig(configId);
            String wsKey = buildWsKey(config);
            String url = String.format(WSS_URL_TEMPLATE, wsKey);
            log.info("智能机器人长链连接: configId={}, url={}", configId, maskUrl(url));

            BotConnection connection = new BotConnection(configId);
            connections.put(configId, connection);
            WebSocketHandler handler = createHandler(configId, config, connection);
            webSocketClient.doHandshake(handler, url).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("智能机器人长链连接失败: configId={}", configId, e);
            connections.remove(configId);
        }
    }

    /**
     * 断开指定配置的智能机器人连接
     */
    public void disconnect(Long configId) {
        BotConnection conn = connections.get(configId);
        if (conn != null) {
            try {
                if (conn.getSession() != null && conn.getSession().isOpen()) {
                    conn.getSession().close();
                }
            } catch (Exception e) {
                log.warn("关闭智能机器人连接时出错: configId={}", configId, e);
            }
            connections.remove(configId);
            log.info("智能机器人长链已断开: configId={}", configId);
        }
    }

    /**
     * 断开所有连接
     */
    public void disconnectAll() {
        for (Long configId : connections.keySet()) {
            disconnect(configId);
        }
    }

    /**
     * 通过应用消息 API 发送文本消息
     */
    public void sendText(Long configId, String content) throws Exception {
        sendText(configId, content, null);
    }

    public void sendText(Long configId, String content, String toUser) throws Exception {
        if (!StringUtils.hasText(content)) {
            return;
        }
        WeComIntelligentBotConfig config = loadConfig(configId);
        WxCpService service = buildTempService(config);
        WxCpMessage message = WxCpMessage.TEXT()
                .toUser(toUser != null ? toUser : "@all")
                .content(content)
                .build();
        var result = service.getMessageService().send(message);
        log.info("智能机器人发送文本消息: configId={}, toUser={}, result={}", configId, toUser, result);
    }

    /**
     * 通过应用消息 API 发送 Markdown 消息
     */
    public void sendMarkdown(Long configId, String content, String toUser) throws Exception {
        if (!StringUtils.hasText(content)) {
            return;
        }
        WeComIntelligentBotConfig config = loadConfig(configId);
        WxCpService service = buildTempService(config);
        WxCpMessage message = WxCpMessage.MARKDOWN()
                .toUser(toUser != null ? toUser : "@all")
                .content(content)
                .build();
        var result = service.getMessageService().send(message);
        log.info("智能机器人发送 Markdown 消息: configId={}, toUser={}, result={}", configId, toUser, result);
    }

    /**
     * 通过应用消息 API 发送文件消息
     */
    public void sendFile(Long configId, File file, String toUser) throws Exception {
        if (file == null || !file.exists()) {
            throw new IllegalArgumentException("文件不存在: " + (file == null ? "null" : file.getAbsolutePath()));
        }
        WeComIntelligentBotConfig config = loadConfig(configId);
        WxCpService service = buildTempService(config);
        String mediaId;
        try (FileInputStream fis = new FileInputStream(file)) {
            var uploadResult = service.getMediaService().upload("file", file.getCanonicalPath(), fis);
            mediaId = uploadResult.getMediaId();
        }
        WxCpMessage message = WxCpMessage.FILE()
                .toUser(toUser != null ? toUser : "@all")
                .mediaId(mediaId)
                .build();
        var result = service.getMessageService().send(message);
        log.info("智能机器人发送文件消息: configId={}, toUser={}, fileName={}, result={}", configId, toUser, file.getName(), result);
    }

    /**
     * 构建长链 wskey: Base64(corpId + "\n" + botId + "\n" + timestamp + "\n" + nonce)
     */
    private String buildWsKey(WeComIntelligentBotConfig config) {
        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String nonce = java.util.UUID.randomUUID().toString().substring(0, 8);
        String raw = config.getCorpId() + "\n" + config.getBotId() + "\n" + timestamp + "\n" + nonce;
        return Base64.getEncoder().encodeToString(raw.getBytes());
    }

    /**
     * 创建 WebSocket 处理器
     */
    private WebSocketHandler createHandler(Long configId, WeComIntelligentBotConfig config, BotConnection connection) {
        AtomicBoolean firstFrame = new AtomicBoolean(true);

        return new WebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                connection.setSession(session);
                log.info("智能机器人长链连接成功: configId={}", configId);
            }

            @Override
            public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
                if (message instanceof org.springframework.web.socket.TextMessage textMsg) {
                    String payload = textMsg.getPayload();
                    log.debug("智能机器人收到消息: configId={}, payload={}", configId, truncate(payload, 500));

                    try {
                        // 第一个消息是鉴权响应
                        if (firstFrame.compareAndSet(true, false)) {
                            Map<String, Object> authResult = objectMapper.readValue(payload, Map.class);
                            int errcode = ((Number) authResult.getOrDefault("errcode", 0)).intValue();
                            if (errcode != 0) {
                                log.error("智能机器人长链鉴权失败: configId={}, response={}", configId, payload);
                                return;
                            }
                            log.info("智能机器人长链鉴权成功: configId={}", configId);
                            return;
                        }

                        // 普通消息处理
                        Map<String, Object> msgMap = objectMapper.readValue(payload, Map.class);
                        String msgType = (String) msgMap.get("msgtype");
                        if (!StringUtils.hasText(msgType)) {
                            return;
                        }

                        String content = "";
                        if ("text".equals(msgType)) {
                            content = (String) msgMap.getOrDefault("content", "");
                        } else if ("markdown".equals(msgType)) {
                            content = objectMapper.writeValueAsString(msgMap.get("markdown"));
                        }

                        String fromUser = (String) msgMap.getOrDefault("from", "");
                        String reply = "";
                        if (StringUtils.hasText(content)) {
                            reply = weComCommandHandler.handleText(content.trim());
                        }
                        if (StringUtils.hasText(reply)) {
                            sendText(configId, reply, fromUser);
                        }
                    } catch (Exception e) {
                        log.error("智能机器人消息处理失败: configId={}", configId, e);
                    }
                }
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) {
                log.info("智能机器人长链断开: configId={}, status={}", configId, status);
                connections.remove(configId);

                // 自动重连
                scheduleReconnect(configId, config);
            }

            @Override
            public void handleTransportError(WebSocketSession session, Throwable error) {
                log.error("智能机器人长链传输错误: configId={}", configId, error);
                connections.remove(configId);
                scheduleReconnect(configId, config);
            }

            @Override
            public boolean supportsPartialMessages() {
                return false;
            }
        };
    }

    private void scheduleReconnect(Long configId, WeComIntelligentBotConfig config) {
        new Thread(() -> {
            int delay = RECONNECT_DELAY_SECONDS;
            while (delay <= RECONNECT_MAX_DELAY_SECONDS) {
                try {
                    Thread.sleep(delay * 1000L);
                    log.info("智能机器人尝试重连: configId={}, delay={}s", configId, delay);
                    // Check config is still enabled
                    NotificationConfig nc = notificationConfigMapper.selectById(configId);
                    if (nc != null && nc.getStatus() != null && nc.getStatus() == 1) {
                        connect(configId);
                        return;
                    }
                    log.info("配置已禁用，停止重连: configId={}", configId);
                    return;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.info("智能机器人重连中断: configId={}", configId);
                    return;
                } catch (Exception e) {
                    log.error("智能机器人重连失败: configId={}, delay={}s", configId, delay, e);
                }
                delay = Math.min(delay * 2, RECONNECT_MAX_DELAY_SECONDS);
            }
            log.error("智能机器人重连超时: configId={}", configId);
        }, "wecom-bot-reconnect-" + configId).start();
    }

    /**
     * 加载配置
     */
    private WeComIntelligentBotConfig loadConfig(Long configId) throws Exception {
        NotificationConfig nc = notificationConfigMapper.selectById(configId);
        if (nc == null) {
            throw new IllegalArgumentException("通知配置不存在: " + configId);
        }
        if (!"WECOM_INTELLIGENT_BOT".equals(nc.getConfigType())) {
            throw new IllegalArgumentException("配置类型不是企业微信智能机器人: " + configId);
        }
        if (nc.getStatus() == null || nc.getStatus() != 1) {
            throw new IllegalArgumentException("配置已禁用: " + configId);
        }
        WeComIntelligentBotConfig config = objectMapper.readValue(nc.getConfigJson(), WeComIntelligentBotConfig.class);
        if (StringUtils.hasText(config.getBotSecret())) {
            config.setBotSecret(CryptoUtil.decryptIfNeeded(config.getBotSecret()));
        }
        return config;
    }

    /**
     * 构建临时 WxCpService（智能机器人使用 botSecret 和 botId）
     */
    private WxCpService buildTempService(WeComIntelligentBotConfig config) {
        WxCpDefaultConfigImpl storage = new WxCpDefaultConfigImpl();
        storage.setCorpId(config.getCorpId());
        storage.setAgentId(Integer.valueOf(config.getBotId()));
        storage.setCorpSecret(config.getBotSecret());
        storage.setApacheHttpClientBuilder(new WeComHttpClientLoggingBuilder());
        WxCpServiceImpl impl = new WxCpServiceImpl();
        impl.setWxCpConfigStorage(storage);
        return impl;
    }

    private String maskUrl(String url) {
        if (url == null) return null;
        return url.replaceAll("wskey=[^&]+", "wskey=***");
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }

    /**
     * 连接句柄
     */
    private static class BotConnection {
        private final Long configId;
        private final AtomicBoolean connected = new AtomicBoolean(true);
        private WebSocketSession session;

        BotConnection(Long configId) {
            this.configId = configId;
        }

        boolean isConnected() {
            return connected.get();
        }

        WebSocketSession getSession() {
            return session;
        }

        void setSession(WebSocketSession session) {
            this.session = session;
        }
    }
}