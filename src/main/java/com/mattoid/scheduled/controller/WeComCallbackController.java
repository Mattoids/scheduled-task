package com.mattoid.scheduled.controller;

import com.mattoid.scheduled.dto.CommandResult;
import com.mattoid.scheduled.service.wecom.WeComAppManager;
import com.mattoid.scheduled.service.wecom.WeComCommandHandler;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.cp.bean.message.WxCpXmlMessage;
import me.chanjar.weixin.cp.util.crypto.WxCpCryptUtil;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/api/wecom/callback")
public class WeComCallbackController {

    private final WeComAppManager weComAppManager;
    private final WeComCommandHandler weComCommandHandler;
    private final Executor taskExecutor;

    /**
     * 回调消息去重缓存：企业微信在 5 秒内未收到响应会重推同一条消息（最多 3 次），
     * 用 MsgId（无 MsgId 时用发送方+时间+内容兜底）做幂等，避免重复回复。
     * TTL 10 分钟远大于企微重试窗口，上限防止内存无限增长。
     */
    private final Cache<String, Boolean> dedupeCache = CacheBuilder.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .maximumSize(10_000)
            .build();

    public WeComCallbackController(WeComAppManager weComAppManager,
                                   WeComCommandHandler weComCommandHandler,
                                   @Qualifier("taskExecutor") Executor taskExecutor) {
        this.weComAppManager = weComAppManager;
        this.weComCommandHandler = weComCommandHandler;
        this.taskExecutor = taskExecutor;
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

            String dedupeKey = buildDedupeKey(configId, message);
            if (dedupeCache.getIfPresent(dedupeKey) != null) {
                log.warn("企业微信重复回调，已忽略: configId={}, key={}", configId, dedupeKey);
                return "success";
            }
            dedupeCache.put(dedupeKey, Boolean.TRUE);

            // 异步处理 + 主动回复，立即返回 success，避免触发企业微信 5 秒超时重推导致重复回复。
            taskExecutor.execute(() -> processAndReply(configId, message));
            return "success";
        } catch (Exception e) {
            log.error("企业微信消息处理失败: configId={}", configId, e);
            return "success";
        }
    }

    /**
     * 异步执行业务处理并通过主动消息接口回复用户。已在回调线程中完成 MsgId 去重，
     * 此处不再回退到被动回复（HTTP 响应早已结束），主动接口失败仅记录日志。
     */
    private void processAndReply(Long configId, WxCpXmlMessage message) {
        try {
            CommandResult result = weComCommandHandler.handle(message, configId);
            log.info("企业微信消息处理完成: configId={}, text={}, hasImage={}", configId,
                    result != null ? result.getText() : null,
                    result != null && result.hasImage());
            String reply = result != null && result.hasText() ? result.getText() : null;
            try {
                if (StringUtils.hasText(reply)) {
                    weComAppManager.sendText(configId, message.getFromUserName(), reply);
                    log.info("企业微信主动回复文本消息发送成功: configId={}, toUser={}", configId, message.getFromUserName());
                }
                if (result != null && result.hasImage()) {
                    weComAppManager.sendImage(configId, message.getFromUserName(), result.getImageFile());
                    log.info("企业微信主动回复图片消息发送成功: configId={}, toUser={}", configId, message.getFromUserName());
                }
            } catch (Exception sendEx) {
                log.error("企业微信主动回复发送失败: configId={}, toUser={}", configId, message.getFromUserName(), sendEx);
            }
        } catch (Exception e) {
            log.error("企业微信消息处理失败: configId={}", configId, e);
        }
    }

    /** 构造去重 key：优先用 MsgId，事件类等无 MsgId 的消息用发送方+时间+类型+内容兜底。 */
    private String buildDedupeKey(Long configId, WxCpXmlMessage message) {
        Long msgId = message.getMsgId();
        if (msgId != null) {
            return configId + ":" + msgId;
        }
        return configId + ":" + message.getFromUserName() + ":" + message.getCreateTime()
                + ":" + message.getMsgType() + ":" + message.getContent() + ":" + message.getEventKey();
    }
}
