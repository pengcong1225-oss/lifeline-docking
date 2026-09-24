package com.lifeline.docking.support;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 新平台自签令牌。
 *
 * <p>新平台<b>不把老平台令牌返回给调用者</b>，新令牌与老令牌不要求互认。</p>
 *
 * <p><b>阻塞项</b>：老平台示例令牌自带 {@code Bearer } 前缀，需确认调用者是否会重复拼接。
 * 这里签发的令牌本身不含 {@code Bearer }，由调用者按 {@code Authorization: Bearer <token>} 组装。</p>
 */
public final class TokenCodec {

    private static final SecureRandom RANDOM = new SecureRandom();

    private TokenCodec() {
    }

    public static String generate() {
        byte[] buf = new byte[32];
        RANDOM.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    /**
     * 从 Authorization 头解析令牌，容忍大小写与多余空白，并去掉可能重复的 Bearer 前缀。
     */
    public static String parseBearer(String header) {
        if (header == null) {
            return null;
        }
        String v = header.trim();
        if (v.length() >= 7 && v.regionMatches(true, 0, "Bearer ", 0, 7)) {
            v = v.substring(7).trim();
        }
        return v.isEmpty() ? null : v;
    }

    public static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    public static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
