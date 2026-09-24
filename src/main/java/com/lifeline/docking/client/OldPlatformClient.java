package com.lifeline.docking.client;

import com.lifeline.docking.config.DockingProperties;
import com.lifeline.docking.model.ApiResponse;
import com.lifeline.docking.support.SignatureSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 老平台客户端：内部取老令牌 + 转发业务数据。
 *
 * <p>老平台业务接口要求 {@code Authorization: Bearer <token>}，因此“用调用者原凭证转发”
 * 在技术上仍包含内部获取老令牌；不能把 accessKey/secretKey 直接当业务接口的 Authorization。</p>
 */
@Component
public class OldPlatformClient {

    private final DockingProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public OldPlatformClient(DockingProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getOldPlatform().getConnectTimeoutMs()))
                .build();
    }

    /**
     * 用调用者的老凭证向老平台换取老令牌。
     *
     * <p><b>阻塞项</b>：接口页把 accessKey/timestamp/signature 标为 query 参数，
     * 接入说明写 {@code multipart/form-data}。这里默认按 query 发送，
     * 可通过 {@code lifeline.old-platform.token-param-style=query|form} 切换以便联调比对。</p>
     */
    public TokenResult fetchToken(String accessKey, String secretKey, String timestamp) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("accessKey", accessKey);
        params.put("timestamp", timestamp);
        String signature = sign(params, secretKey);
        params.put("signature", signature);

        String base = properties.getOldPlatform().getBaseUrl() + properties.getOldPlatform().getTokenPath();
        String style = properties.getOldPlatform().getTokenParamStyle();
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .timeout(Duration.ofMillis(properties.getOldPlatform().getReadTimeoutMs()));
            if ("form".equalsIgnoreCase(style)) {
                builder.header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                        .POST(HttpRequest.BodyPublishers.ofString(formEncode(params), StandardCharsets.UTF_8));
            } else {
                builder.POST(HttpRequest.BodyPublishers.noBody());
                base = base + "?" + formEncode(params);
            }
            HttpResponse<String> response = httpClient.send(builder.uri(URI.create(base)).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return TokenResult.fail("老平台取令牌返回 HTTP " + response.statusCode(), response.body());
            }
            String token = extractToken(response.body());
            if (token == null) {
                // 解析不出令牌时不能假装成功，交由调用方按“结果未知”处理。
                return TokenResult.fail("老平台取令牌响应中未找到令牌", response.body());
            }
            return TokenResult.ok(token, response.body());
        } catch (Exception e) {
            return TokenResult.fail("老平台取令牌异常：" + e.getMessage(), null);
        }
    }

    /**
     * 用老令牌转发原始业务数据。请求体原样转发，不做字段改写。
     */
    public ForwardResult forward(String oldToken, String rawBody) {
        String url = properties.getOldPlatform().getBaseUrl() + properties.getOldPlatform().getExecutePath();
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(properties.getOldPlatform().getReadTimeoutMs()))
                    .header("Authorization", "Bearer " + oldToken)
                    .header("Content-Type", "application/json;charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(rawBody, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return ForwardResult.unknown("老平台返回 HTTP " + response.statusCode(), response.body());
            }
            return ForwardResult.reached(response.body());
        } catch (Exception e) {
            // 超时或断线：结果未知。不得伪装成功，也不得自动重放。
            return ForwardResult.unknown("转发老平台异常，结果未知：" + e.getMessage(), null);
        }
    }

    private String sign(Map<String, String> params, String secretKey) {
        Map<String, String> withoutSignature = new LinkedHashMap<>(params);
        withoutSignature.remove("signature");
        if ("given".equalsIgnoreCase(properties.getSignature().getParamOrder())) {
            return SignatureSupport.signInOrder(
                    java.util.List.of("accessKey", "timestamp"), withoutSignature, secretKey);
        }
        return SignatureSupport.signSorted(withoutSignature, secretKey);
    }

    private static String formEncode(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        params.forEach((k, v) -> {
            if (sb.length() > 0) {
                sb.append('&');
            }
            sb.append(URLEncoder.encode(k, StandardCharsets.UTF_8))
              .append('=')
              .append(URLEncoder.encode(v == null ? "" : v, StandardCharsets.UTF_8));
        });
        return sb.toString();
    }

    /**
     * 从老平台取令牌响应中提取令牌。
     *
     * <p>兼容两种文档差异：接口页 Schema 是空对象，接入说明给出 {@code data.token}；
     * 且示例令牌自带 {@code Bearer } 前缀，这里统一剥离，避免调用方重复拼接。</p>
     */
    public String extractToken(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode root = mapper.readTree(body);
            JsonNode node = root.path("data").path("token");
            if (node.isMissingNode() || node.isNull()) {
                node = root.path("token");
            }
            if (node.isMissingNode() || node.isNull()) {
                node = root.path("data");
            }
            if (node.isMissingNode() || node.isNull() || !node.isValueNode()) {
                return null;
            }
            String token = node.asText();
            if (token.regionMatches(true, 0, "Bearer ", 0, 7)) {
                token = token.substring(7).trim();
            }
            return token.isBlank() ? null : token;
        } catch (Exception e) {
            return null;
        }
    }

    /** 解析老平台响应中的业务码；解析不出来时返回 null，由调用方按结果未知处理。 */
    public Integer parseCode(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode root = mapper.readTree(body);
            JsonNode code = root.path("code");
            return code.isNumber() ? code.asInt() : (code.isMissingNode() ? null : Integer.valueOf(code.asText()));
        } catch (Exception e) {
            return null;
        }
    }

    /** 取令牌结果。 */
    public record TokenResult(boolean ok, String token, String rawBody, String message) {
        public static TokenResult ok(String token, String rawBody) {
            return new TokenResult(true, token, rawBody, "成功");
        }
        public static TokenResult fail(String message, String rawBody) {
            return new TokenResult(false, null, rawBody, message);
        }
    }

    /** 转发结果。httpReached=false 表示超时/断线，业务结果未知。 */
    public record ForwardResult(boolean httpReached, String rawBody, String message) {
        public static ForwardResult reached(String rawBody) {
            return new ForwardResult(true, rawBody, "已送达");
        }
        public static ForwardResult unknown(String message, String rawBody) {
            return new ForwardResult(false, rawBody, message);
        }
    }
}
