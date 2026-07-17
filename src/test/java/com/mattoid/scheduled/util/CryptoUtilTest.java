package com.mattoid.scheduled.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class CryptoUtilTest {

    private static final String TEST_KEY = "0123456789abcdef";

    @BeforeEach
    void setUp() {
        CryptoUtil.initialize(TEST_KEY);
    }

    @Test
    void encryptShouldUseGcmFormat() {
        String plain = "hello world";
        String encrypted = CryptoUtil.encrypt(plain);

        assertNotNull(encrypted);
        assertTrue(encrypted.startsWith(CryptoUtil.GCM_PREFIX), "密文应使用 GCM 前缀");
        assertTrue(encrypted.endsWith(CryptoUtil.SUFFIX), "密文应以 ) 结尾");
        assertNotEquals(plain, encrypted);

        assertEquals(plain, CryptoUtil.decryptIfNeeded(encrypted));
    }

    @Test
    void encryptDecryptUnicodeText() {
        String plain = "中文密码：@#$%^\u0026*()_+";
        String encrypted = CryptoUtil.encrypt(plain);
        assertEquals(plain, CryptoUtil.decryptIfNeeded(encrypted));
    }

    @Test
    void encryptNullOrBlankReturnsOriginal() {
        assertNull(CryptoUtil.encrypt(null));
        assertEquals("", CryptoUtil.encrypt(""));
        assertEquals("   ", CryptoUtil.encrypt("   "));
    }

    @Test
    void decryptPlaintextReturnsOriginal() {
        String plain = "plain text";
        assertEquals(plain, CryptoUtil.decryptIfNeeded(plain));
    }

    @Test
    void decryptLegacyEcbReturnsPlaintext() throws Exception {
        String plain = "legacy-ecb-secret";
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(TEST_KEY.getBytes(StandardCharsets.UTF_8), "AES"));
        String legacy = CryptoUtil.LEGACY_PREFIX
                + Base64.getEncoder().encodeToString(cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8)))
                + CryptoUtil.SUFFIX;

        assertEquals(plain, CryptoUtil.decryptIfNeeded(legacy));
    }

    @Test
    void tamperedGcmCiphertextShouldThrow() {
        String encrypted = CryptoUtil.encrypt("sensitive data");
        // 篡改中间一个 Base64 字符（保留 GCM 格式外壳）
        char[] chars = encrypted.toCharArray();
        int idx = chars.length / 2;
        chars[idx] = chars[idx] == 'A' ? 'B' : 'A';
        String tampered = new String(chars);

        assertTrue(tampered.startsWith(CryptoUtil.GCM_PREFIX));
        assertThrows(IllegalStateException.class, () -> CryptoUtil.decryptIfNeeded(tampered));
    }

    @Test
    void isEncryptedDetectsBothFormats() {
        String gcm = CryptoUtil.encrypt("x");
        assertTrue(CryptoUtil.isEncrypted(gcm));

        String legacy = CryptoUtil.LEGACY_PREFIX + "abc" + CryptoUtil.SUFFIX;
        assertTrue(CryptoUtil.isEncrypted(legacy));

        assertFalse(CryptoUtil.isEncrypted("plain"));
        assertFalse(CryptoUtil.isEncrypted(null));
        assertFalse(CryptoUtil.isEncrypted(""));
    }
}
