package com.mattoid.scheduled.service.wecom;

import com.mattoid.scheduled.entity.WeComAppConfig;
import com.mattoid.scheduled.mapper.WeComAppConfigMapper;
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

    private final WeComAppConfigMapper weComAppConfigMapper;
    private final Map<Long, WxCpService> serviceCache = new ConcurrentHashMap<>();

    public WeComAppManager(WeComAppConfigMapper weComAppConfigMapper) {
        this.weComAppConfigMapper = weComAppConfigMapper;
    }

    public WxCpService getService(Long configId) {
        WxCpService service = serviceCache.get(configId);
        if (service != null) {
            return service;
        }
        WeComAppConfig config = weComAppConfigMapper.selectById(configId);
        if (config == null) {
            throw new IllegalArgumentException("企业微信应用配置不存在: " + configId);
        }
        if (config.getStatus() == null || config.getStatus() != 1) {
            throw new IllegalArgumentException("企业微信应用配置已禁用: " + configId);
        }

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

        WxCpServiceImpl impl = new WxCpServiceImpl();
        impl.setWxCpConfigStorage(storage);
        serviceCache.put(configId, impl);
        return impl;
    }

    public void invalidateCache(Long configId) {
        serviceCache.remove(configId);
    }

    public String getAccessToken(Long configId) throws Exception {
        return getService(configId).getAccessToken();
    }

    public void createMenu(Long configId, String menuJson) throws Exception {
        WxCpService service = getService(configId);
        WxMenu menu = WxMenu.fromJson(menuJson);
        service.getMenuService().create(menu);
        log.info("企业微信应用菜单创建成功: {}", configId);
    }

    public void sendText(Long configId, String toUser, String content) throws Exception {
        if (!StringUtils.hasText(content)) {
            return;
        }
        WxCpService service = getService(configId);
        WxCpMessage message = WxCpMessage.TEXT()
                .toUser(toUser)
                .content(content)
                .build();
        service.getMessageService().send(message);
        log.info("企业微信应用文本消息发送成功: configId={}, toUser={}", configId, toUser);
    }

    public void sendFile(Long configId, String toUser, File file) throws Exception {
        if (file == null || !file.exists()) {
            throw new IllegalArgumentException("文件不存在: " + (file == null ? "null" : file.getAbsolutePath()));
        }
        WxCpService service = getService(configId);
        String mediaId;
        try (FileInputStream fis = new FileInputStream(file)) {
            mediaId = service.getMediaService()
                    .upload("file", file.getName(), fis)
                    .getMediaId();
        }
        WxCpMessage message = WxCpMessage.FILE()
                .toUser(toUser)
                .mediaId(mediaId)
                .build();
        service.getMessageService().send(message);
        log.info("企业微信应用文件消息发送成功: configId={}, toUser={}, file={}", configId, toUser, file.getName());
    }

    public String verifyUrl(Long configId, String signature, String timestamp, String nonce, String echostr) throws Exception {
        WeComAppConfig config = weComAppConfigMapper.selectById(configId);
        if (config == null) {
            throw new IllegalArgumentException("企业微信应用配置不存在: " + configId);
        }
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
        WeComAppConfig config = weComAppConfigMapper.selectById(configId);
        if (config == null) {
            throw new IllegalArgumentException("企业微信应用配置不存在: " + configId);
        }
        WxCpCryptUtil cryptUtil = new WxCpCryptUtil(buildStorage(config));
        String decrypted = cryptUtil.decryptXml(signature, timestamp, nonce, postData);
        return XStreamTransformer.fromXml(WxCpXmlMessage.class, decrypted);
    }

    public WxCpCryptUtil buildCryptUtil(Long configId) {
        WeComAppConfig config = weComAppConfigMapper.selectById(configId);
        if (config == null) {
            throw new IllegalArgumentException("企业微信应用配置不存在: " + configId);
        }
        return new WxCpCryptUtil(buildStorage(config));
    }

    public String encryptReply(Long configId, String reply, String timestamp, String nonce) throws Exception {
        WeComAppConfig config = weComAppConfigMapper.selectById(configId);
        if (config == null) {
            throw new IllegalArgumentException("企业微信应用配置不存在: " + configId);
        }
        if (!StringUtils.hasText(config.getToken()) || !StringUtils.hasText(config.getAesKey())) {
            throw new IllegalArgumentException("企业微信应用配置缺少 Token 或 AES Key");
        }
        WxCpCryptUtil cryptUtil = new WxCpCryptUtil(buildStorage(config));
        String encrypt = cryptUtil.encrypt(reply);
        String signature = SHA1.gen(config.getToken(), timestamp, nonce, encrypt);
        return "<xml>" +
                "<Encrypt><![CDATA[" + encrypt + "]]</Encrypt>" +
                "<MsgSignature><![CDATA[" + signature + "]]</MsgSignature>" +
                "<TimeStamp>" + timestamp + "</TimeStamp>" +
                "<Nonce><![CDATA[" + nonce + "]]</Nonce>" +
                "</xml>";
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
        return storage;
    }
}
