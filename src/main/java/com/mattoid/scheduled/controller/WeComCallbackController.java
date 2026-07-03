package com.mattoid.scheduled.controller;

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
                         @RequestParam("signature") String signature,
                         @RequestParam("timestamp") String timestamp,
                         @RequestParam("nonce") String nonce,
                         @RequestParam("echostr") String echostr) throws Exception {
        log.info("收到企业微信 URL 验证请求: configId={}", configId);
        return weComAppManager.verifyUrl(configId, signature, timestamp, nonce, echostr);
    }

    @PostMapping("/{configId}")
    public String callback(@PathVariable Long configId,
                           @RequestParam("signature") String signature,
                           @RequestParam("timestamp") String timestamp,
                           @RequestParam("nonce") String nonce,
                           HttpServletRequest request) throws Exception {
        String body = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        log.debug("收到企业微信消息: configId={}, body={}", configId, body);

        WxCpXmlMessage message = weComAppManager.parseMessage(configId, signature, timestamp, nonce, body);
        String reply = weComCommandHandler.handle(message, configId);
        if (!StringUtils.hasText(reply)) {
            return "success";
        }
        return weComAppManager.encryptReply(configId, reply, timestamp, nonce);
    }
}
