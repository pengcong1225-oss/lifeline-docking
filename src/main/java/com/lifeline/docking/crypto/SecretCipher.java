package com.lifeline.docking.crypto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * secretKey 的对称加密。
 *
 * <p>算法：AES-256-GCM。存储格式为 {@code Base64(iv(12字节) || ciphertext+tag)}。</p>
 *
 * <p>主密钥来自 {@code lifeline.crypto.master-key}（Base64，解码后 32 字节）。
 * 未配置时生成临时密钥并打印警告——此时已登记的凭证在重启后无法解密，仅供本机联调。</p>
 */
@Component
public class SecretCipher {

    private static final Logger log = LoggerFactory.getLogger(SecretCipher.class);
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public SecretCipher(com.lifeline.docking.config.DockingProperties properties) {
        String configured = properties.getCrypto().getMasterKey();
        byte[] keyBytes;
        if (configured == null || configured.isBlank()) {
            keyBytes = new byte[32];
            random.nextBytes(keyBytes);
            log.warn("========================================================================");
            log.warn("未配置 lifeline.crypto.master-key，已生成临时主密钥。");
            log.warn("本次启动登记的凭证在重启后将无法解密。生产环境必须通过");
            log.warn("LIFELINE_MASTER_KEY 环境变量注入固定的 32 字节 Base64 主密钥。");
            log.warn("生成一个可用密钥：openssl rand -base64 32");
            log.warn("========================================================================");
        } else {
            keyBytes = Base64.getDecoder().decode(configured.trim());
            if (keyBytes.length != 32) {
                throw new IllegalStateException(
                        "lifeline.crypto.master-key 必须是 Base64 编码的 32 字节密钥，当前为 " + keyBytes.length + " 字节");
            }
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    /** 加密明文 secretKey。 */
    public String encrypt(String plainText) {
        if (plainText == null) {
            throw new IllegalArgumentException("待加密内容不能为 null");
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("secretKey 加密失败", e);
        }
    }

    /** 解密。密文损坏或主密钥不匹配时抛出异常，不返回占位值。 */
    public String decrypt(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalArgumentException("待解密内容为空");
        }
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);
            if (combined.length <= IV_LENGTH) {
                throw new IllegalArgumentException("密文长度不足");
            }
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            byte[] cipherText = new byte[combined.length - IV_LENGTH];
            System.arraycopy(combined, IV_LENGTH, cipherText, 0, cipherText.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("secretKey 解密失败：主密钥可能已更换", e);
        }
    }

    /** 只保留尾部若干位，用于页面展示时人工核对密钥，避免回显完整密钥。 */
    public static String hint(String plainSecret) {
        if (plainSecret == null || plainSecret.isEmpty()) {
            return "";
        }
        int keep = Math.min(4, plainSecret.length());
        return "****" + plainSecret.substring(plainSecret.length() - keep);
    }
}
