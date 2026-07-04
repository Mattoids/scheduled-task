package com.mattoid.scheduled.service.wecom;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mattoid.scheduled.entity.NotificationConfig;
import com.mattoid.scheduled.entity.WeComAppConfig;
import com.mattoid.scheduled.mapper.NotificationConfigMapper;
import com.mattoid.scheduled.util.CryptoUtil;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.bean.menu.WxMenu;
import me.chanjar.weixin.common.util.crypto.SHA1;
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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
        if (!"WECOM_APP".equals(notificationConfig.getConfigType())) {
            throw new IllegalArgumentException("配置类型不是企业微信应用: " + configId);
        }
        if (notificationConfig.getStatus() == null || notificationConfig.getStatus() != 1) {
            throw new IllegalArgumentException("企业微信应用配置已禁用: " + configId);
        }
        return objectMapper.readValue(notificationConfig.getConfigJson(), WeComAppConfig.class);
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
        log.info("企业微信应用发送文本消息: configId={}, toUser={}, contentLength={}, content={}",
                configId, toUser, content.length(), truncate(content, 500));
        WxCpService service = getService(configId);
        WxCpMessage message = WxCpMessage.TEXT()
                .toUser(toUser)
                .content(content)
                .build();
        var result = service.getMessageService().send(message);
        log.info("企业微信应用文本消息发送完成: configId={}, toUser={}, result={}", configId, toUser, result);
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
        WxCpMessage message = WxCpMessage.FILE()
                .toUser(toUser)
                .mediaId(mediaId)
                .build();
        var result = service.getMessageService().send(message);
        log.info("企业微信应用文件消息发送完成: configId={}, toUser={}, fileName={}, result={}",
                configId, toUser, file.getName(), result);
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

    private WxCpDefaultConfigImpl buildStorage(WeComAppConfig config) {
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
            log.info("企业微信应用使用自定义 API 地址: baseApiUrl={}", baseApiUrl);
            storage.setBaseApiUrl(baseApiUrl);
        } else {
            log.info("企业微信应用使用默认 API 地址");
        }
        storage.setApacheHttpClientBuilder(new WeComHttpClientLoggingBuilder());
        return storage;
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }
}
