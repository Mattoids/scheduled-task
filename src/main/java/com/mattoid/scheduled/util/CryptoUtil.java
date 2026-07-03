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
    private static final String KEY = "ScheduledTask#01";

    public static String encrypt(String plain) {
        if (!StringUtils.hasText(plain)) {
            return plain;
        }
        try {
            SecretKeySpec keySpec = new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
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
            SecretKeySpec keySpec = new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec);
            byte[] decoded = Base64.getDecoder().decode(base64);
            return new String(cipher.doFinal(decoded), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Decrypt failed", e);
            return cipherText;
        }
    }
}
