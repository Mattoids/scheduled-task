package com.mattoid.scheduled.controller;

import com.mattoid.scheduled.dto.CommandResult;
import com.mattoid.scheduled.service.wecom.WeComAppManager;
import com.mattoid.scheduled.service.wecom.WeComCommandHandler;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.cp.bean.message.WxCpXmlMessage;
import me.chanjar.weixin.cp.util.crypto.WxCpCryptUtil;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@Slf4j
@RestController
@RequestMapping("/api/wecom/callback")
public class WeComCallbackController {

    private final WeComAppManager weComAppManager;
    private final WeComCommandHandler weComCommandHandler;

    public WeComCallbackController(WeComAppManager weComAppManager,
                                   WeComCommandHandler weComCommandHandler) {
        this.weComAppManager = weComAppManager;
        this.weComCommandHandler = weComCommandHandler;
    }

    @GetMapping("/{configId}")
    public String verify(@PathVariable Long configId,
                         @RequestParam(value = "msg_signature", required = false) String msgSignature,
                         @RequestParam(value = "signature", required = false) String signature,
                         @RequestParam(value = "timestamp", required = false) String timestamp,
                         @RequestParam(value = "nonce", required = false) String nonce,
                         @RequestParam(value = "echostr", required = false) String echostr) throws Exception {
        String actualSignature = StringUtils.hasText(msgSignature) ? msgSignature : signature;
        if (!StringUtils.hasText(actualSignature) || !StringUtils.hasText(timestamp)
                || !StringUtils.hasText(nonce) || !StringUtils.hasText(echostr)) {
            log.warn("企业微信回调 URL 验证参数缺失: configId={}, msg_signature={}, signature={}, timestamp={}, nonce={}, echostr={}",
                    configId, msgSignature, signature, timestamp, nonce, echostr);
            return "参数缺失";
        }
        log.info("收到企业微信 URL 验证请求: configId={}", configId);
        return weComAppManager.verifyUrl(configId, actualSignature, timestamp, nonce, echostr);
    }

    @PostMapping("/{configId}")
    public String callback(@PathVariable Long configId,
                           @RequestParam(value = "msg_signature", required = false) String msgSignature,
                           @RequestParam(value = "signature", required = false) String signature,
                           @RequestParam(value = "timestamp", required = false) String timestamp,
                           @RequestParam(value = "nonce", required = false) String nonce,
                           HttpServletRequest request) throws Exception {
        String actualSignature = StringUtils.hasText(msgSignature) ? msgSignature : signature;
        if (!StringUtils.hasText(actualSignature) || !StringUtils.hasText(timestamp) || !StringUtils.hasText(nonce)) {
            log.warn("企业微信消息回调参数缺失: configId={}, msg_signature={}, signature={}, timestamp={}, nonce={}",
                    configId, msgSignature, signature, timestamp, nonce);
            return "success";
        }
        String body = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        log.info("收到企业微信消息回调: configId={}, body={}", configId, body);

        try {
            // 长链（WebSocket）智能机器人通过 WeComIntelligentBotClient 收发消息，不走 HTTP 回调；
            // 若企业微信仍向本端点 POST（例如管理后台误填了回调 URL），直接确认收下并忽略，避免按应用模式解密失败刷堆栈。
            if (weComAppManager.isLongChainBot(configId)) {
                log.warn("收到长链智能机器人的 HTTP 回调，已忽略（长链消息经 WebSocket 处理）: configId={}", configId);
                return "success";
            }
            WxCpXmlMessage message = weComAppManager.parseMessage(configId, actualSignature, timestamp, nonce, body);
            log.info("企业微信消息解析成功: configId={}, fromUser={}, msgType={}, content={}, eventKey={}",
                    configId, message.getFromUserName(), message.getMsgType(), message.getContent(), message.getEventKey());
            CommandResult result = weComCommandHandler.handle(message, configId);
            log.info("企业微信消息处理完成: configId={}, text={}, hasImage={}", configId,
                    result != null ? result.getText() : null,
                    result != null && result.hasImage());
            String reply = result != null && result.hasText() ? result.getText() : null;

            // 优先使用主动消息接口回复用户，失败时回退到被动回复
            try {
                if (StringUtils.hasText(reply)) {
                    weComAppManager.sendText(configId, message.getFromUserName(), reply);
                    log.info("企业微信主动回复文本消息发送成功: configId={}, toUser={}", configId, message.getFromUserName());
                }
                if (result != null && result.hasImage()) {
                    weComAppManager.sendImage(configId, message.getFromUserName(), result.getImageFile());
                    log.info("企业微信主动回复图片消息发送成功: configId={}, toUser={}", configId, message.getFromUserName());
                }
                return "success";
            } catch (Exception sendEx) {
                log.error("企业微信主动回复消息发送失败，尝试被动回复: configId={}", configId, sendEx);
                if (!StringUtils.hasText(reply)) {
                    return "success";
                }
                String encrypted = weComAppManager.encryptReply(configId, reply,
                        message.getFromUserName(), message.getToUserName(), timestamp, nonce);
                log.info("企业微信被动回复消息已生成: configId={}", configId);
                return encrypted;
            }
        } catch (Exception e) {
            log.error("企业微信消息处理失败: configId={}", configId, e);
            return "success";
        }
    }
}
