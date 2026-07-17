package com.mattoid.scheduled.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

@Slf4j
public class CryptoUtil {

    private static final String ALGORITHM = "AES";
    private static final String ECB_TRANSFORMATION = "AES/ECB/PKCS5Padding";
    private static final String GCM_TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private static final String KEY_ENV = "SCHEDULED_TASK_AES_KEY";
    private static final String KEY_PROP = "scheduled.task.aes.key";

    public static final String LEGACY_PREFIX = "ENC(";
    public static final String GCM_PREFIX = "GCM(";
    public static final String SUFFIX = ")";

    private static volatile SecretKey legacyKeySpec;
    private static volatile SecretKey gcmKeySpec;
    private static volatile boolean initialized = false;

    /**
     * 显式初始化 AES 密钥，优先级高于环境变量/JVM 参数。
     * 为兼容历史 ECB 数据保留原始 UTF-8 字节作为旧密钥；
     * 新写入的 GCM 数据使用 SHA-256 派生的 256-bit 密钥。
     */
    public static synchronized void initialize(String key) {
        if (!StringUtils.hasText(key)) {
            return;
        }
        try {
            byte[] rawBytes = key.getBytes(StandardCharsets.UTF_8);
            legacyKeySpec = new SecretKeySpec(rawBytes, ALGORITHM);
            byte[] derived = MessageDigest.getInstance("SHA-256").digest(rawBytes);
            gcmKeySpec = new SecretKeySpec(derived, ALGORITHM);
            initialized = true;
        } catch (Exception e) {
            throw new IllegalStateException("初始化 AES 密钥失败", e);
        }
    }

    private static void ensureKeys() {
        if (gcmKeySpec == null) {
            synchronized (CryptoUtil.class) {
                if (gcmKeySpec == null) {
                    if (!initialized) {
                        String key = System.getenv(KEY_ENV);
                        if (!StringUtils.hasText(key)) {
                            key = System.getProperty(KEY_PROP);
                        }
                        if (StringUtils.hasText(key)) {
                            initialize(key);
                        }
                    }
                    if (gcmKeySpec == null) {
                        throw new IllegalStateException(
                                "AES 密钥未配置。请设置环境变量 " + KEY_ENV + "、JVM 参数 -D" + KEY_PROP + "= 或在 application.yml 中配置 scheduled.task.aes.key");
                    }
                }
            }
        }
    }

    /**
     * 判断值是否已被加密（旧版 ENC(...) 或新版 GCM(...)）。
     */
    public static boolean isEncrypted(String value) {
        return StringUtils.hasText(value)
                && (value.startsWith(LEGACY_PREFIX) || value.startsWith(GCM_PREFIX))
                && value.endsWith(SUFFIX);
    }

    /**
     * 使用 AES-GCM 加密明文，返回 GCM(base64(iv + ciphertext + authTag))。
     */
    public static String encrypt(String plain) {
        if (!StringUtils.hasText(plain)) {
            return plain;
        }
        ensureKeys();
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(GCM_TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, gcmKeySpec, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[GCM_IV_LENGTH + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, GCM_IV_LENGTH);
            System.arraycopy(encrypted, 0, combined, GCM_IV_LENGTH, encrypted.length);

            return GCM_PREFIX + Base64.getEncoder().encodeToString(combined) + SUFFIX;
        } catch (Exception e) {
            log.error("Encrypt failed", e);
            return plain;
        }
    }

    /**
     * 自动识别并解密 GCM(...) 或旧版 ENC(...)。
     * GCM 密文被篡改或密钥错误时会抛出 IllegalStateException；
     * 旧版 ECB 解密失败时按历史行为返回原密文。
     */
    public static String decryptIfNeeded(String cipherText) {
        if (!StringUtils.hasText(cipherText)) {
            return cipherText;
        }
        ensureKeys();
        if (cipherText.startsWith(GCM_PREFIX) && cipherText.endsWith(SUFFIX)) {
            return decryptGcm(cipherText);
        }
        if (cipherText.startsWith(LEGACY_PREFIX) && cipherText.endsWith(SUFFIX)) {
            return decryptEcb(cipherText);
        }
        return cipherText;
    }

    private static String decryptGcm(String cipherText) {
        String base64 = cipherText.substring(GCM_PREFIX.length(), cipherText.length() - SUFFIX.length());
        byte[] combined = Base64.getDecoder().decode(base64);
        if (combined.length < GCM_IV_LENGTH) {
            throw new IllegalStateException("GCM 密文长度不足");
        }
        byte[] iv = Arrays.copyOfRange(combined, 0, GCM_IV_LENGTH);
        byte[] encrypted = Arrays.copyOfRange(combined, GCM_IV_LENGTH, combined.length);
        try {
            Cipher cipher = Cipher.getInstance(GCM_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, gcmKeySpec, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("GCM 解密失败，数据可能被篡改", e);
        }
    }

    private static String decryptEcb(String cipherText) {
        String base64 = cipherText.substring(LEGACY_PREFIX.length(), cipherText.length() - SUFFIX.length());
        try {
            Cipher cipher = Cipher.getInstance(ECB_TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, legacyKeySpec);
            byte[] decoded = Base64.getDecoder().decode(base64);
            return new String(cipher.doFinal(decoded), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Legacy ECB decrypt failed", e);
            return cipherText;
        }
    }
}
