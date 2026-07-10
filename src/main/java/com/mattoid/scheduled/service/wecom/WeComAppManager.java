package com.mattoid.scheduled.service.wecom;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mattoid.scheduled.entity.NotificationConfig;
import com.mattoid.scheduled.entity.WeComAppConfig;
import com.mattoid.scheduled.mapper.NotificationConfigMapper;
import com.mattoid.scheduled.util.CryptoUtil;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.bean.menu.WxMenu;
import me.chanjar.weixin.common.util.crypto.SHA1;
import me.chanjar.weixin.common.error.WxErrorException;
import me.chanjar.weixin.cp.api.WxCpService;
import me.chanjar.weixin.cp.api.impl.WxCpServiceImpl;
import me.chanjar.weixin.cp.bean.message.WxCpMessage;
import me.chanjar.weixin.cp.bean.message.WxCpXmlMessage;
import me.chanjar.weixin.cp.config.impl.WxCpDefaultConfigImpl;
import me.chanjar.weixin.cp.util.crypto.WxCpCryptUtil;
import me.chanjar.weixin.cp.util.xml.XStreamTransformer;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
public class WeComAppManager {

    private final NotificationConfigMapper notificationConfigMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<Long, WxCpService> serviceCache = new ConcurrentHashMap<>();

    public WeComAppManager(NotificationConfigMapper notificationConfigMapper) {
        this.notificationConfigMapper = notificationConfigMapper;
    }

    private WeComAppConfig loadConfig(Long configId) throws Exception {
        NotificationConfig notificationConfig = notificationConfigMapper.selectById(configId);
        if (notificationConfig == null) {
            throw new IllegalArgumentException("企业微信应用配置不存在: " + configId);
        }
        if (!"WECOM_APP".equals(notificationConfig.getConfigType())
                && !"WECOM_INTELLIGENT_BOT".equals(notificationConfig.getConfigType())) {
            throw new IllegalArgumentException("配置类型不是企业微信应用或智能机器人回调模式: " + configId);
        }
        if (notificationConfig.getStatus() == null || notificationConfig.getStatus() != 1) {
            throw new IllegalArgumentException("企业微信应用配置已禁用: " + configId);
        }
        return objectMapper.readValue(notificationConfig.getConfigJson(), WeComAppConfig.class);
    }

    /**
     * 是否为「智能机器人-长链（WebSocket）模式」。该模式通过 {@link WeComIntelligentBotClient} 长链收发消息，
     * 不应进入 HTTP 回调（/api/wecom/callback/{id}）：回调入口缺少长链所需的 botId/botSecret 解密流程，
     * 强行按应用模式解密只会失败并刷出错误堆栈。供回调控制器提前识别并忽略此类请求。
     */
    public boolean isLongChainBot(Long configId) {
        NotificationConfig nc = notificationConfigMapper.selectById(configId);
        if (nc == null || !"WECOM_INTELLIGENT_BOT".equals(nc.getConfigType())) {
            return false;
        }
        if (!StringUtils.hasText(nc.getConfigJson())) {
            return false;
        }
        try {
            Map<?, ?> map = objectMapper.readValue(nc.getConfigJson(), Map.class);
            return "LONGCHAIN".equalsIgnoreCase(String.valueOf(map.get("mode")));
        } catch (Exception e) {
            return false;
        }
    }

    public WxCpService getService(Long configId) {
        WxCpService service = serviceCache.get(configId);
        if (service != null) {
            return service;
        }
        try {
            WeComAppConfig config = loadConfig(configId);
            WxCpDefaultConfigImpl storage = buildStorage(config);

            WxCpServiceImpl impl = new WxCpServiceImpl();
            impl.setWxCpConfigStorage(storage);
            serviceCache.put(configId, impl);
            return impl;
        } catch (Exception e) {
            throw new IllegalArgumentException("加载企业微信应用配置失败: " + configId, e);
        }
    }

    public void invalidateCache(Long configId) {
        serviceCache.remove(configId);
    }

    public String getAccessToken(Long configId) throws Exception {
        return getService(configId).getAccessToken();
    }

    public void createMenu(Long configId, String menuJson) throws Exception {
        WxCpService service = getService(configId);
        String parseJson = menuJson;
        if (!menuJson.trim().startsWith("{\"menu\"")) {
            parseJson = "{\"menu\":" + menuJson + "}";
        }
        log.info("企业微信应用创建菜单: configId={}, menuJson={}", configId, truncate(parseJson, 1000));
        WxMenu menu = WxMenu.fromJson(parseJson);
        service.getMenuService().create(menu);
        log.info("企业微信应用菜单创建成功: configId={}", configId);
    }

    public void sendText(Long configId, String toUser, String content) throws Exception {
        if (!StringUtils.hasText(content)) {
            return;
        }
        WxCpService service = getService(configId);
        for (String user : splitToUsers(toUser)) {
            try {
                log.info("企业微信应用发送文本消息: configId={}, toUser={}, contentLength={}, content={}",
                        configId, user, content.length(), truncate(content, 500));
                WxCpMessage message = WxCpMessage.TEXT()
                        .toUser(user)
                        .content(content)
                        .build();
                var result = service.getMessageService().send(message);
                log.info("企业微信应用文本消息发送完成: configId={}, toUser={}, result={}", configId, user, result);
            } catch (WxErrorException e) {
                log.warn("企业微信应用发送文本消息失败: configId={}, toUser={}, error={}", configId, user, e.getMessage());
            }
        }
    }

    public void sendMarkdown(Long configId, String toUser, String content) throws Exception {
        if (!StringUtils.hasText(content)) {
            return;
        }
        WxCpService service = getService(configId);
        for (String user : splitToUsers(toUser)) {
            try {
                log.info("企业微信应用发送 Markdown 消息: configId={}, toUser={}, contentLength={}, content={}",
                        configId, user, content.length(), truncate(content, 500));
                WxCpMessage message = WxCpMessage.MARKDOWN()
                        .toUser(user)
                        .content(content)
                        .build();
                var result = service.getMessageService().send(message);
                log.info("企业微信应用 Markdown 消息发送完成: configId={}, toUser={}, result={}", configId, user, result);
            } catch (WxErrorException e) {
                log.warn("企业微信应用发送 Markdown 消息失败: configId={}, toUser={}, error={}", configId, user, e.getMessage());
            }
        }
    }

    public void sendFile(Long configId, String toUser, File file) throws Exception {
        if (file == null || !file.exists()) {
            throw new IllegalArgumentException("文件不存在: " + (file == null ? "null" : file.getAbsolutePath()));
        }
        log.info("企业微信应用发送文件消息: configId={}, toUser={}, fileName={}, fileSize={}",
                configId, toUser, file.getName(), file.length());
        WxCpService service = getService(configId);
        String mediaId;
        try (FileInputStream fis = new FileInputStream(file)) {
            var uploadResult = service.getMediaService().upload("file", file.getName(), fis);
            mediaId = uploadResult.getMediaId();
            log.info("企业微信应用文件上传完成: configId={}, mediaIdLength={}, mediaType={}",
                    configId,
                    mediaId != null ? mediaId.length() : 0,
                    uploadResult.getType());
        }
        for (String user : splitToUsers(toUser)) {
            try {
                WxCpMessage message = WxCpMessage.FILE()
                        .toUser(user)
                        .mediaId(mediaId)
                        .build();
                var result = service.getMessageService().send(message);
                log.info("企业微信应用文件消息发送完成: configId={}, toUser={}, fileName={}, result={}",
                        configId, user, file.getName(), result);
            } catch (WxErrorException e) {
                log.warn("企业微信应用发送文件消息失败: configId={}, toUser={}, fileName={}, error={}",
                        configId, user, file.getName(), e.getMessage());
            }
        }
    }

    public void sendImage(Long configId, String toUser, File imageFile) throws Exception {
        if (imageFile == null || !imageFile.exists()) {
            throw new IllegalArgumentException("图片文件不存在: " + (imageFile == null ? "null" : imageFile.getAbsolutePath()));
        }
        log.info("企业微信应用发送图片消息: configId={}, toUser={}, fileName={}, fileSize={}",
                configId, toUser, imageFile.getName(), imageFile.length());
        WxCpService service = getService(configId);
        String mediaId;
        try (FileInputStream fis = new FileInputStream(imageFile)) {
            var uploadResult = service.getMediaService().upload("image", imageFile.getName(), fis);
            mediaId = uploadResult.getMediaId();
            log.info("企业微信应用图片上传完成: configId={}, mediaIdLength={}",
                    configId, mediaId != null ? mediaId.length() : 0);
        }
        for (String user : splitToUsers(toUser)) {
            try {
                WxCpMessage message = WxCpMessage.IMAGE()
                        .toUser(user)
                        .mediaId(mediaId)
                        .build();
                var result = service.getMessageService().send(message);
                log.info("企业微信应用图片消息发送完成: configId={}, toUser={}, result={}",
                        configId, user, result);
            } catch (WxErrorException e) {
                log.warn("企业微信应用发送图片消息失败: configId={}, toUser={}, error={}",
                        configId, user, e.getMessage());
            }
        }
    }

    public String verifyUrl(Long configId, String signature, String timestamp, String nonce, String echostr) throws Exception {
        WeComAppConfig config = loadConfig(configId);
        if (!StringUtils.hasText(config.getToken()) || !StringUtils.hasText(config.getAesKey())) {
            throw new IllegalArgumentException("企业微信应用配置缺少 Token 或 AES Key");
        }
        String expected = SHA1.gen(config.getToken(), timestamp, nonce, echostr);
        if (!expected.equals(signature)) {
            throw new IllegalArgumentException("URL 验证签名不匹配");
        }
        WxCpCryptUtil cryptUtil = new WxCpCryptUtil(buildStorage(config));
        return cryptUtil.decrypt(echostr);
    }

    public WxCpXmlMessage parseMessage(Long configId, String signature, String timestamp, String nonce, String postData) throws Exception {
        WeComAppConfig config = loadConfig(configId);
        WxCpCryptUtil cryptUtil = new WxCpCryptUtil(buildStorage(config));
        String decrypted = cryptUtil.decryptXml(signature, timestamp, nonce, postData);
        return XStreamTransformer.fromXml(WxCpXmlMessage.class, decrypted);
    }

    public WxCpCryptUtil buildCryptUtil(Long configId) throws Exception {
        WeComAppConfig config = loadConfig(configId);
        return new WxCpCryptUtil(buildStorage(config));
    }

    public String encryptReply(Long configId, String reply, String toUser, String fromUser,
                               String timestamp, String nonce) throws Exception {
        WeComAppConfig config = loadConfig(configId);
        if (!StringUtils.hasText(config.getToken()) || !StringUtils.hasText(config.getAesKey())) {
            throw new IllegalArgumentException("企业微信应用配置缺少 Token 或 AES Key");
        }
        WxCpCryptUtil cryptUtil = new WxCpCryptUtil(buildStorage(config));
        String replyXml = "<xml>" +
                "<ToUserName><![CDATA[" + toUser + "]]></ToUserName>" +
                "<FromUserName><![CDATA[" + fromUser + "]]></FromUserName>" +
                "<CreateTime>" + (System.currentTimeMillis() / 1000) + "</CreateTime>" +
                "<MsgType><![CDATA[text]]></MsgType>" +
                "<Content><![CDATA[" + reply + "]]></Content>" +
                "</xml>";
        // WxCpCryptUtil.encrypt 已经返回包含 Encrypt/MsgSignature/TimeStamp/Nonce 的完整 XML
        return cryptUtil.encrypt(replyXml);
    }

    public WxCpService buildTempService(WeComAppConfig config) {
        WxCpDefaultConfigImpl storage = buildStorage(config);
        WxCpServiceImpl impl = new WxCpServiceImpl();
        impl.setWxCpConfigStorage(storage);
        return impl;
    }

    public static WxCpDefaultConfigImpl buildStorage(WeComAppConfig config) {
        WxCpDefaultConfigImpl storage = new WxCpDefaultConfigImpl();
        storage.setCorpId(config.getCorpId());
        storage.setAgentId(config.getAgentId());
        storage.setCorpSecret(CryptoUtil.decryptIfNeeded(config.getSecret()));
        if (StringUtils.hasText(config.getToken())) {
            storage.setToken(config.getToken());
        }
        if (StringUtils.hasText(config.getAesKey())) {
            storage.setAesKey(config.getAesKey());
        }
        if (StringUtils.hasText(config.getProxyUrl())) {
            String baseApiUrl = config.getProxyUrl().trim();
            storage.setBaseApiUrl(baseApiUrl);
        }
        storage.setApacheHttpClientBuilder(new WeComHttpClientLoggingBuilder());
        return storage;
    }

    private List<String> splitToUsers(String toUser) {
        if (!StringUtils.hasText(toUser)) {
            return List.of("@all");
        }
        if ("@all".equals(toUser.trim())) {
            return List.of("@all");
        }
        return Arrays.stream(toUser.split("\\|"))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }
}
