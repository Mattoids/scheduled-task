package com.mattoid.scheduled.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Slf4j
public class CryptoUtil {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";
    private static final String KEY_ENV = "SCHEDULED_TASK_AES_KEY";
    private static final String KEY_PROP = "scheduled.task.aes.key";

    private static volatile SecretKeySpec keySpec;
    private static volatile boolean initialized = false;

    /**
     * 显式初始化 AES 密钥，优先级高于环境变量/JVM 参数。
     * 通常在 Spring 配置类中调用，将配置文件中的 scheduled.task.aes.key 注入。
     */
    public static synchronized void initialize(String key) {
        if (!StringUtils.hasText(key)) {
            return;
        }
        try {
            // 保持与历史数据兼容：直接使用 UTF-8 字节作为 AES 密钥（原硬编码逻辑）
            byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
            keySpec = new SecretKeySpec(keyBytes, ALGORITHM);
            initialized = true;
        } catch (Exception e) {
            throw new IllegalStateException("初始化 AES 密钥失败", e);
        }
    }

    private static SecretKeySpec getKeySpec() {
        if (keySpec == null) {
            synchronized (CryptoUtil.class) {
                if (keySpec == null) {
                    if (!initialized) {
                        String key = System.getenv(KEY_ENV);
                        if (!StringUtils.hasText(key)) {
                            key = System.getProperty(KEY_PROP);
                        }
                        if (StringUtils.hasText(key)) {
                            initialize(key);
                        }
                    }
                    if (keySpec == null) {
                        throw new IllegalStateException(
                                "AES 密钥未配置。请设置环境变量 " + KEY_ENV + "、JVM 参数 -D" + KEY_PROP + "= 或在 application.yml 中配置 scheduled.task.aes.key");
                    }
                }
            }
        }
        return keySpec;
    }

    public static String encrypt(String plain) {
        if (!StringUtils.hasText(plain)) {
            return plain;
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, getKeySpec());
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return "ENC(" + Base64.getEncoder().encodeToString(encrypted) + ")";
        } catch (Exception e) {
            log.error("Encrypt failed", e);
            return plain;
        }
    }

    public static String decryptIfNeeded(String cipherText) {
        if (!StringUtils.hasText(cipherText) || !cipherText.startsWith("ENC(") || !cipherText.endsWith(")")) {
            return cipherText;
        }
        String base64 = cipherText.substring(4, cipherText.length() - 1);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, getKeySpec());
            byte[] decoded = Base64.getDecoder().decode(base64);
            return new String(cipher.doFinal(decoded), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Decrypt failed", e);
            return cipherText;
        }
    }
}
