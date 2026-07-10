package com.mattoid.scheduled.service.wecom;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mattoid.scheduled.dto.CommandResult;
import com.mattoid.scheduled.entity.NotificationConfig;
import com.mattoid.scheduled.entity.WeComIntelligentBotConfig;
import com.mattoid.scheduled.mapper.NotificationConfigMapper;
import com.mattoid.scheduled.util.CryptoUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
public class WeComIntelligentBotClient {

    /**
     * 企业微信智能机器人长连接官方地址
     */
    private static final String DEFAULT_LONG_CONNECTION_URL = "wss://openws.work.weixin.qq.com";

    private static final String CMD_SUBSCRIBE = "aibot_subscribe";
    private static final String CMD_PING = "ping";
    private static final String CMD_RESPONSE = "aibot_respond_msg";
    private static final String CMD_MSG_CALLBACK = "aibot_msg_callback";
    private static final String CMD_EVENT_CALLBACK = "aibot_event_callback";

    private static final long PING_INTERVAL_MS = 30_000;
    private static final long TEST_TIMEOUT_MS = 10_000;
    private static final long RECONNECT_DELAY_SECONDS = 5;
    private static final long RECONNECT_MAX_DELAY_SECONDS = 60;

    /**
     * 用于对回调里的 response_url 发起 HTTP POST 投递回复。JDK 内置客户端，复用实例即可。
     */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private final NotificationConfigMapper notificationConfigMapper;
    private final WeComCommandHandler weComCommandHandler;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebSocketClient webSocketClient;
    private final Map<Long, BotConnection> connections = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeatExecutor = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "wecom-intelligent-bot-heartbeat");
        t.setDaemon(true);
        return t;
    });

    public WeComIntelligentBotClient(NotificationConfigMapper notificationConfigMapper,
                                     WeComCommandHandler weComCommandHandler) {
        this.notificationConfigMapper = notificationConfigMapper;
        this.weComCommandHandler = weComCommandHandler;
        this.webSocketClient = new StandardWebSocketClient();
    }

    /**
     * 测试智能机器人长连接配置是否可用
     */
    public void testConnection(WeComIntelligentBotConfig config) throws Exception {
        validateLongConnectionConfig(config);
        String decryptedSecret = CryptoUtil.decryptIfNeeded(config.getBotSecret());

        CountDownLatch subscribedLatch = new CountDownLatch(1);
        AtomicReference<String> errorRef = new AtomicReference<>();

        WebSocketHandler handler = new WebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                log.info("智能机器人测试连接已建立");
                sendSubscribe(session, config.getBotId(), decryptedSecret);
            }

            @Override
            public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
                String payload = getTextPayload(message);
                log.debug("智能机器人测试连接收到消息: {}", truncate(payload, 500));
                try {
                    Map<String, Object> msg = objectMapper.readValue(payload, Map.class);
                    String cmd = (String) msg.get("cmd");
                    Map<String, Object> headers = (Map<String, Object>) msg.get("headers");
                    String reqId = headers != null ? (String) headers.get("req_id") : null;

                    if (isSubscribeResponse(cmd, reqId)) {
                        int errcode = extractErrcode(msg);
                        String errmsg = extractErrmsg(msg);
                        if (errcode == 0) {
                            log.info("智能机器人测试订阅成功");
                            subscribedLatch.countDown();
                        } else {
                            log.error("智能机器人测试订阅失败, errcode={}, errmsg={}", errcode, errmsg);
                            errorRef.set("智能机器人订阅失败, errcode=" + errcode + ", errmsg=" + errmsg);
                            subscribedLatch.countDown();
                        }
                        safeClose(session);
                    }
                } catch (Exception e) {
                    log.warn("智能机器人测试连接处理消息失败", e);
                }
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) {
                // 测试连接无需处理关闭事件
            }

            @Override
            public void handleTransportError(WebSocketSession session, Throwable error) {
                errorRef.set("智能机器人测试连接传输错误: " + error.getMessage());
                subscribedLatch.countDown();
            }

            @Override
            public boolean supportsPartialMessages() {
                return false;
            }
        };

        WebSocketSession session = webSocketClient.execute(handler, new org.springframework.web.socket.WebSocketHttpHeaders(), URI.create(DEFAULT_LONG_CONNECTION_URL)).get(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        boolean success = subscribedLatch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        safeClose(session);

        if (!success) {
            throw new IllegalStateException("智能机器人测试连接超时");
        }
        String error = errorRef.get();
        if (error != null) {
            throw new IllegalStateException(error);
        }
    }

    /**
     * 通过通知配置 ID 连接智能机器人长链
     */
    public void connect(Long configId) {
        BotConnection existing = connections.get(configId);
        if (existing != null) {
            if (existing.isConnected()) {
                log.info("智能机器人连接已存在: configId={}", configId);
                return;
            }
            // 旧连接未就绪，先清理
            existing.stop();
            connections.remove(configId);
        }

        try {
            WeComIntelligentBotConfig config = loadConfig(configId);
            log.info("智能机器人长链连接: configId={}, botId={}", configId, maskBotId(config.getBotId()));

            BotConnection connection = new BotConnection(configId);
            connections.put(configId, connection);
            WebSocketHandler handler = createHandler(configId, config, connection);
            webSocketClient.execute(handler, new org.springframework.web.socket.WebSocketHttpHeaders(), URI.create(DEFAULT_LONG_CONNECTION_URL)).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("智能机器人长链连接失败: configId={}", configId, e);
            connections.remove(configId);
        }
    }

    /**
     * 断开指定配置的智能机器人连接
     */
    public void disconnect(Long configId) {
        BotConnection conn = connections.remove(configId);
        if (conn != null) {
            conn.stop();
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
        heartbeatExecutor.shutdown();
    }

    /**
     * 通过智能机器人长链发送文本消息。
     * 注意：官方长连接模式下机器人只能被动回复用户消息，不支持主动向指定用户发送消息。
     */
    public void sendText(Long configId, String content) throws Exception {
        throw new UnsupportedOperationException(
                "智能机器人长链模式仅支持被动回复用户消息，不支持主动发送消息。如需主动推送通知，请使用 CALLBACK 模式。");
    }

    /**
     * 通过智能机器人长链发送文本消息。
     * 注意：官方长连接模式下机器人只能被动回复用户消息，不支持主动向指定用户发送消息。
     */
    public void sendText(Long configId, String content, String toUser) throws Exception {
        throw new UnsupportedOperationException(
                "智能机器人长链模式仅支持被动回复用户消息，不支持主动发送消息。如需主动推送通知，请使用 CALLBACK 模式。");
    }

    /**
     * 通过智能机器人长链发送 Markdown 消息。
     * 注意：官方长连接模式下机器人只能被动回复用户消息，不支持主动向指定用户发送消息。
     */
    public void sendMarkdown(Long configId, String content, String toUser) throws Exception {
        throw new UnsupportedOperationException(
                "智能机器人长链模式仅支持被动回复用户消息，不支持主动发送消息。如需主动推送通知，请使用 CALLBACK 模式。");
    }

    /**
     * 通过智能机器人长链发送文件消息。
     * 注意：官方长连接模式下机器人只能被动回复用户消息，不支持主动向指定用户发送消息。
     */
    public void sendFile(Long configId, File file, String toUser) throws Exception {
        throw new UnsupportedOperationException(
                "智能机器人长链模式仅支持被动回复用户消息，不支持主动发送消息。如需主动推送通知，请使用 CALLBACK 模式。");
    }

    private WebSocketHandler createHandler(Long configId, WeComIntelligentBotConfig config, BotConnection connection) {
        String decryptedSecret = CryptoUtil.decryptIfNeeded(config.getBotSecret());

        return new WebSocketHandler() {
            @Override
            public void afterConnectionEstablished(WebSocketSession session) throws Exception {
                connection.setSession(session);
                connection.clearReconnectTask();
                log.info("智能机器人长链连接成功: configId={}", configId);
                sendSubscribe(session, config.getBotId(), decryptedSecret);
            }

            @Override
            public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
                String payload = getTextPayload(message);

                try {
                    Map<String, Object> msg = objectMapper.readValue(payload, Map.class);
                    String cmd = (String) msg.get("cmd");
                    Map<String, Object> headers = (Map<String, Object>) msg.get("headers");
                    String reqId = headers != null ? (String) headers.get("req_id") : null;

                    if (isSubscribeResponse(cmd, reqId)) {
                        int errcode = extractErrcode(msg);
                        String errmsg = extractErrmsg(msg);
                        if (errcode == 0) {
                            connection.setSubscribed(true);
                            connection.setConnected(true);
                            connection.setReconnectAttempts(0);
                            startHeartbeat(configId, connection);
                            log.info("智能机器人长链订阅成功: configId={}", configId);
                        } else {
                            log.error("智能机器人长链订阅失败: configId={}, errcode={}, errmsg={}", configId, errcode, errmsg);
                            scheduleReconnect(configId, connection);
                        }
                        return;
                    }

                    if (CMD_PING.equals(cmd) || (reqId != null && reqId.startsWith(CMD_PING + "_"))) {
                        log.debug("智能机器人长链收到心跳响应: configId={}", configId);
                        return;
                    }

                    if (!connection.isSubscribed()) {
                        log.warn("智能机器人长链尚未订阅成功，忽略消息: configId={}", configId);
                        return;
                    }

                    if (CMD_MSG_CALLBACK.equals(cmd) || CMD_EVENT_CALLBACK.equals(cmd)) {
                        Map<String, Object> body = (Map<String, Object>) msg.get("body");
                        if (body == null) {
                            log.warn("智能机器人长链收到回调但无 body: configId={}", configId);
                            return;
                        }
                        String msgType = (String) body.get("msgtype");
                        if (!StringUtils.hasText(msgType)) {
                            log.warn("智能机器人长链收到回调但无 msgtype: configId={}", configId);
                            return;
                        }

                        String content = "";
                        if ("text".equals(msgType)) {
                            content = extractTextContent(body);
                        } else if ("markdown".equals(msgType)) {
                            Object md = body.get("markdown");
                            content = md != null ? objectMapper.writeValueAsString(md) : "";
                        }

                        String fromUser = extractFromUser(body);
                        log.info("智能机器人长链收到消息: configId={}, reqId={}, msgType={}, fromUser={}, content={}, raw={}",
                                configId, reqId, msgType, fromUser, truncate(content, 200), truncate(payload, 800));

                        String reply = "";
                        CommandResult result = null;
                        if (StringUtils.hasText(content)) {
                            result = weComCommandHandler.handleText(content.trim(), fromUser);
                        }
                        if (result != null && result.hasText()) {
                            reply = result.getText();
                        }
                        if (result != null && result.hasImage()) {
                            log.warn("智能机器人长链模式暂不支持发送图片，已忽略图表: configId={}", configId);
                        }
                        if (StringUtils.hasText(reply)) {
                            // 长链回复的正确投递通道是回调 body 里的 response_url（含一次性 response_code），
                            // 走 WebSocket 发 aibot_respond_msg 只会被 ACK、不会落到用户会话。
                            String responseUrl = (String) body.get("response_url");
                            if (StringUtils.hasText(responseUrl)) {
                                log.info("智能机器人长链准备回复: configId={}, fromUser={}, reply={}",
                                        configId, fromUser, truncate(reply, 200));
                                sendReplyViaResponseUrl(responseUrl, reply, configId, fromUser);
                            } else {
                                log.warn("智能机器人长链回调缺少 response_url，无法投递回复: configId={}, fromUser={}, msgType={}",
                                        configId, fromUser, msgType);
                            }
                        } else {
                            log.info("智能机器人长链无需回复: configId={}, fromUser={}", configId, fromUser);
                        }
                    } else {
                        log.debug("智能机器人长链收到其他消息: configId={}, cmd={}", configId, cmd);
                    }
                } catch (Exception e) {
                    log.error("智能机器人消息处理失败: configId={}, payload={}", configId, truncate(payload, 500), e);
                }
            }

            @Override
            public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) {
                log.info("智能机器人长链断开: configId={}, status={}", configId, status);
                connection.setConnected(false);
                connection.setSubscribed(false);
                connection.stopHeartbeat();
                scheduleReconnect(configId, connection);
            }

            @Override
            public void handleTransportError(WebSocketSession session, Throwable error) {
                log.error("智能机器人长链传输错误: configId={}", configId, error);
                connection.setConnected(false);
                connection.setSubscribed(false);
                connection.stopHeartbeat();
                scheduleReconnect(configId, connection);
            }

            @Override
            public boolean supportsPartialMessages() {
                return false;
            }
        };
    }

    private void sendSubscribe(WebSocketSession session, String botId, String secret) throws IOException {
        Map<String, Object> payload = Map.of(
                "cmd", CMD_SUBSCRIBE,
                "headers", Map.of("req_id", CMD_SUBSCRIBE + "_" + UUID.randomUUID().toString().replace("-", "")),
                "body", Map.of("bot_id", botId, "secret", secret)
        );
        sendJson(session, payload);
    }

    private void sendPing(WebSocketSession session, String reqId) throws IOException {
        Map<String, Object> payload = Map.of(
                "cmd", CMD_PING,
                "headers", Map.of("req_id", CMD_PING + "_" + reqId)
        );
        sendJson(session, payload);
    }

    /**
     * 通过回调里携带的 response_url（含一次性 response_code）投递回复。
     * 企业微信智能机器人长链采用「长连接收、HTTP 回」模型：WebSocket 上发 aibot_respond_msg 只会被 ACK、
     * 不会把消息投递到用户会话；必须对该 response_url 发起 HTTP POST，body 为 {msgtype,text:{content}}。
     */
    private void sendReplyViaResponseUrl(String responseUrl, String content, Long configId, String fromUser) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("msgtype", "text");
        body.put("text", Map.of("content", content));
        String json;
        try {
            json = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            log.error("智能机器人回复序列化失败: configId={}", configId, e);
            return;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(responseUrl))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();
            log.info("智能机器人长链通过 response_url 回复: configId={}, fromUser={}, body={}",
                    configId, fromUser, truncate(json, 500));
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("智能机器人长链 response_url 回复结果: configId={}, status={}, body={}",
                    configId, response.statusCode(), truncate(response.body(), 500));
        } catch (Exception e) {
            log.error("智能机器人长链 response_url 回复失败: configId={}, fromUser={}", configId, fromUser, e);
        }
    }

    private void sendJson(WebSocketSession session, Map<String, Object> payload) throws IOException {
        if (session == null || !session.isOpen()) {
            throw new IllegalStateException("WebSocket 未连接");
        }
        String cmd = extractCmd(payload);
        String reqId = extractReqId(payload);
        String json = objectMapper.writeValueAsString(payload);
        try {
            log.info("智能机器人长链发送消息: cmd={}, reqId={}", cmd, reqId);
            session.sendMessage(new org.springframework.web.socket.TextMessage(json));
            log.info("智能机器人长链发送成功: cmd={}, reqId={}", cmd, reqId);
        } catch (IOException e) {
            log.error("智能机器人长链发送失败: cmd={}, reqId={}", cmd, reqId, e);
            throw e;
        }
    }

    private String extractCmd(Map<String, Object> payload) {
        Object cmd = payload != null ? payload.get("cmd") : null;
        return cmd != null ? cmd.toString() : "";
    }

    private String extractReqId(Map<String, Object> payload) {
        if (payload == null) {
            return "";
        }
        Object headers = payload.get("headers");
        if (headers instanceof Map<?, ?> h) {
            Object reqId = h.get("req_id");
            return reqId != null ? reqId.toString() : "";
        }
        return "";
    }

    private String getTextPayload(WebSocketMessage<?> message) {
        if (message instanceof org.springframework.web.socket.TextMessage textMsg) {
            return textMsg.getPayload();
        }
        return String.valueOf(message.getPayload());
    }

    private void startHeartbeat(Long configId, BotConnection connection) {
        connection.stopHeartbeat();
        connection.setHeartbeatTask(heartbeatExecutor.scheduleAtFixedRate(() -> {
            try {
                WebSocketSession session = connection.getSession();
                if (session != null && session.isOpen()) {
                    sendPing(session, UUID.randomUUID().toString().replace("-", ""));
                } else {
                    log.warn("智能机器人长链心跳时连接已断开: configId={}", configId);
                    scheduleReconnect(configId, connection);
                }
            } catch (Exception e) {
                log.warn("智能机器人长链心跳失败: configId={}", configId, e);
            }
        }, PING_INTERVAL_MS, PING_INTERVAL_MS, TimeUnit.MILLISECONDS));
    }

    private void validateLongConnectionConfig(WeComIntelligentBotConfig config) {
        if (!StringUtils.hasText(config.getBotId())) {
            throw new IllegalArgumentException("智能机器人 BotId 不能为空");
        }
        if (!StringUtils.hasText(config.getBotSecret())) {
            throw new IllegalArgumentException("智能机器人 Secret 不能为空");
        }
    }

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
        validateLongConnectionConfig(config);
        if (StringUtils.hasText(config.getBotSecret())) {
            config.setBotSecret(CryptoUtil.decryptIfNeeded(config.getBotSecret()));
        }
        return config;
    }

    private String maskBotId(String botId) {
        if (!StringUtils.hasText(botId) || botId.length() <= 8) {
            return "***";
        }
        return botId.substring(0, 4) + "***" + botId.substring(botId.length() - 4);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) return null;
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }

    private void safeClose(WebSocketSession session) {
        if (session != null && session.isOpen()) {
            try {
                session.close();
            } catch (IOException e) {
                log.debug("关闭测试连接失败", e);
            }
        }
    }

    private boolean isSubscribeResponse(String cmd, String reqId) {
        return CMD_SUBSCRIBE.equals(cmd) || (reqId != null && reqId.startsWith(CMD_SUBSCRIBE + "_"));
    }

    private int extractErrcode(Map<String, Object> msg) {
        Map<String, Object> body = (Map<String, Object>) msg.get("body");
        Number errcodeNum = body != null ? (Number) body.get("errcode") : null;
        if (errcodeNum == null) {
            errcodeNum = (Number) msg.get("errcode");
        }
        return errcodeNum != null ? errcodeNum.intValue() : -1;
    }

    private String extractErrmsg(Map<String, Object> msg) {
        Map<String, Object> body = (Map<String, Object>) msg.get("body");
        String errmsg = body != null ? (String) body.get("errmsg") : null;
        if (errmsg == null) {
            errmsg = (String) msg.get("errmsg");
        }
        return errmsg;
    }

    /**
     * 从回调 body 中提取文本内容。企业微信智能机器人长链回调文本位于 body.text.content（text 为对象），
     * 而非 body.content；这里优先取 text.content，兼容兜底 body.content。
     */
    private String extractTextContent(Map<String, Object> body) {
        Object text = body.get("text");
        if (text instanceof Map<?, ?> m) {
            Object c = m.get("content");
            if (c != null) {
                return c.toString();
            }
        }
        Object direct = body.get("content");
        return direct != null ? direct.toString() : "";
    }

    /**
     * 从回调 body 中提取发送者 userid。from 字段为对象 { "userid": "xxx" }，不能直接强转 String。
     */
    private String extractFromUser(Map<String, Object> body) {
        Object from = body.get("from");
        if (from instanceof Map<?, ?> m) {
            Object uid = m.get("userid");
            return uid != null ? uid.toString() : "";
        }
        return from != null ? from.toString() : "";
    }

    /**
     * 连接句柄
     */
    private void scheduleReconnect(Long configId, BotConnection connection) {
        if (connection.isStopped()) {
            return;
        }
        connection.incrementReconnectAttempts();
        long delay = Math.min(RECONNECT_DELAY_SECONDS * (1L << Math.min(connection.getReconnectAttempts(), 10)),
                RECONNECT_MAX_DELAY_SECONDS);
        log.info("智能机器人长链计划重连: configId={}, delay={}s", configId, delay);
        java.util.concurrent.ScheduledFuture<?> task = heartbeatExecutor.schedule(() -> {
            if (!connection.isStopped()) {
                connect(configId);
            }
        }, delay, TimeUnit.SECONDS);
        connection.setReconnectTask(task);
    }

    private static class BotConnection {
        private final Long configId;
        private final AtomicBoolean connected = new AtomicBoolean(false);
        private final AtomicBoolean subscribed = new AtomicBoolean(false);
        private WebSocketSession session;
        private java.util.concurrent.ScheduledFuture<?> heartbeatTask;
        private java.util.concurrent.ScheduledFuture<?> reconnectTask;
        private int reconnectAttempts = 0;
        private volatile boolean stopped = false;

        BotConnection(Long configId) {
            this.configId = configId;
        }

        boolean isConnected() {
            return connected.get();
        }

        void setConnected(boolean connected) {
            this.connected.set(connected);
        }

        boolean isSubscribed() {
            return subscribed.get();
        }

        void setSubscribed(boolean subscribed) {
            this.subscribed.set(subscribed);
        }

        WebSocketSession getSession() {
            return session;
        }

        void setSession(WebSocketSession session) {
            this.session = session;
        }

        int getReconnectAttempts() {
            return reconnectAttempts;
        }

        void incrementReconnectAttempts() {
            this.reconnectAttempts++;
        }

        void setReconnectAttempts(int reconnectAttempts) {
            this.reconnectAttempts = reconnectAttempts;
        }

        void setHeartbeatTask(java.util.concurrent.ScheduledFuture<?> task) {
            this.heartbeatTask = task;
        }

        void stopHeartbeat() {
            if (heartbeatTask != null) {
                heartbeatTask.cancel(false);
                heartbeatTask = null;
            }
        }

        void setReconnectTask(java.util.concurrent.ScheduledFuture<?> task) {
            clearReconnectTask();
            this.reconnectTask = task;
        }

        void clearReconnectTask() {
            if (reconnectTask != null) {
                reconnectTask.cancel(false);
                reconnectTask = null;
            }
        }

        boolean isStopped() {
            return stopped;
        }

        void stop() {
            stopped = true;
            stopHeartbeat();
            clearReconnectTask();
            if (session != null && session.isOpen()) {
                try {
                    session.close();
                } catch (IOException e) {
                    log.debug("关闭连接失败", e);
                }
            }
        }
    }
}
