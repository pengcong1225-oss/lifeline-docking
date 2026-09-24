package com.lifeline.docking.support;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 签名计算与校验。
 *
 * <p><b>阻塞项</b>：源文档只写了“key=value&amp;key=value” + HMAC-SHA256，未定义参数顺序、
 * 字符编码、签名输出格式（hex 大小写）与时间戳容差。这里提供两种常见策略供联调比对，
 * 未拿到真实请求样本或官方测试向量前，不要假定某一种就是老平台的实际规则。</p>
 */
public final class SignatureSupport {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private SignatureSupport() {
    }

    /**
     * 按参数名升序拼接 {@code key=value&key=value} 后计算 HMAC-SHA256。
     *
     * @param params   参与签名的参数（不含 signature 本身）
     * @param secretKey 老平台分配的 secretKey
     * @return 小写 hex 签名
     */
    public static String signSorted(Map<String, String> params, String secretKey) {
        Map<String, String> sorted = new TreeMap<>(params);
        return hmacSha256Hex(join(sorted), secretKey);
    }

    /**
     * 按给定顺序拼接后计算 HMAC-SHA256，用于比对“文档书写顺序”这种可能性。
     */
    public static String signInOrder(List<String> keys, Map<String, String> params, String secretKey) {
        Map<String, String> ordered = new java.util.LinkedHashMap<>();
        for (String k : keys) {
            String v = params.get(k);
            if (v != null) {
                ordered.put(k, v);
            }
        }
        return hmacSha256Hex(join(ordered), secretKey);
    }

    /**
     * 恒定时间比较，避免通过响应时间侧信道泄露签名前缀。
     */
    public static boolean matches(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.trim().getBytes(StandardCharsets.UTF_8));
    }

    public static String hmacSha256Hex(String plainText, String secretKey) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            byte[] raw = mac.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            return hex(raw);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 计算失败", e);
        }
    }

    private static String join(Map<String, String> params) {
        List<String> parts = new ArrayList<>();
        params.forEach((k, v) -> parts.add(k + "=" + (v == null ? "" : v)));
        return String.join("&", parts);
    }

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

    /** 按键升序排序的键列表，便于日志与联调核对。 */
    public static List<String> sortedKeys(Map<String, String> params) {
        List<String> keys = new ArrayList<>(params.keySet());
        keys.sort(Comparator.naturalOrder());
        return keys;
    }
}
