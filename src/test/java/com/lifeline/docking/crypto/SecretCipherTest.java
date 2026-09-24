package com.lifeline.docking.crypto;

import com.lifeline.docking.config.DockingProperties;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecretCipherTest {

    private static final String KEY_A = Base64.getEncoder().encodeToString(repeat((byte) 1));
    private static final String KEY_B = Base64.getEncoder().encodeToString(repeat((byte) 2));

    private static byte[] repeat(byte value) {
        byte[] bytes = new byte[32];
        java.util.Arrays.fill(bytes, value);
        return bytes;
    }

    private static SecretCipher cipher(String base64Key) {
        DockingProperties properties = new DockingProperties();
        properties.getCrypto().setMasterKey(base64Key);
        return new SecretCipher(properties);
    }

    @Test
    void roundTripRestoresPlainSecret() {
        SecretCipher cipher = cipher(KEY_A);
        String secret = "sk-real-secret-value";
        assertEquals(secret, cipher.decrypt(cipher.encrypt(secret)));
    }

    @Test
    void cipherTextIsNotPlainTextAndDiffersPerCall() {
        SecretCipher cipher = cipher(KEY_A);
        String secret = "sk-real-secret-value";
        String first = cipher.encrypt(secret);
        String second = cipher.encrypt(secret);

        assertNotEquals(secret, first);
        assertFalse(first.contains(secret), "密文不得包含明文");
        assertNotEquals(first, second, "每次加密应使用不同 IV");
        assertEquals(secret, cipher.decrypt(first));
        assertEquals(secret, cipher.decrypt(second));
    }

    @Test
    void wrongMasterKeyCannotDecrypt() {
        String encrypted = cipher(KEY_A).encrypt("sk-secret");
        // 更换主密钥后必须解密失败，而不是返回错误内容
        assertThrows(IllegalStateException.class, () -> cipher(KEY_B).decrypt(encrypted));
    }

    @Test
    void tamperedCipherTextIsRejected() {
        SecretCipher cipher = cipher(KEY_A);
        byte[] raw = Base64.getDecoder().decode(cipher.encrypt("sk-secret"));
        raw[raw.length - 1] ^= 0x01; // 篡改最后一个字节
        String tampered = Base64.getEncoder().encodeToString(raw);
        assertThrows(IllegalStateException.class, () -> cipher.decrypt(tampered));
    }

    @Test
    void missingMasterKeyFallsBackToEphemeralKey() {
        // 未配置主密钥时不应启动失败，但要在日志中警告
        SecretCipher cipher = cipher("");
        String encrypted = cipher.encrypt("sk-secret");
        assertEquals("sk-secret", cipher.decrypt(encrypted));
    }

    @Test
    void wrongKeyLengthIsRejected() {
        String tooShort = Base64.getEncoder().encodeToString(new byte[16]);
        assertThrows(IllegalStateException.class, () -> cipher(tooShort));
    }

    @Test
    void hintOnlyExposesTail() {
        assertEquals("****0001", SecretCipher.hint("sk-abcd0001"));
        assertEquals("", SecretCipher.hint(""));
        assertTrue(SecretCipher.hint("sk-abcd0001").length() < "sk-abcd0001".length());
    }
}
