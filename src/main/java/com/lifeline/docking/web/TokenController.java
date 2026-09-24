package com.lifeline.docking.web;

import com.lifeline.docking.config.DockingProperties;
import com.lifeline.docking.entity.CallerCredentialEntity;
import com.lifeline.docking.service.CredentialService;
import com.lifeline.docking.support.SignatureSupport;
import com.lifeline.docking.support.TokenCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code POST /data-docking-api/token}
 *
 * <p>新平台从第一天起独立对外鉴权，签发<b>自己的</b>令牌，不把老平台令牌返回给调用者。</p>
 *
 * <p><b>阻塞项</b>：
 * <ul>
 *   <li>参数位置：接口页写 query，接入说明写 multipart/form-data。这里同时接受两者。</li>
 *   <li>响应格式：接口页 Schema 是 {}，接入说明有 data.token/issuedAt/expiresAt，
 *       且示例令牌带 Bearer 前缀。这里返回接入说明的结构，令牌本身不含 Bearer。</li>
 * </ul>
 * 联调时必须用真实调用者样本核对这两点。</p>
 */
@RestController
public class TokenController {

    private static final Logger log = LoggerFactory.getLogger(TokenController.class);

    private final CredentialService credentials;
    private final DockingProperties properties;
    /** 新平台自签令牌。第一版放内存，重启即失效；正式实现应换成可靠存储。 */
    private final Map<String, IssuedToken> issued = new ConcurrentHashMap<>();

    public TokenController(CredentialService credentials, DockingProperties properties) {
        this.credentials = credentials;
        this.properties = properties;
    }

    @PostMapping(value = "/data-docking-api/token",
            consumes = {"application/x-www-form-urlencoded", "multipart/form-data", "*/*"})
    public ResponseEntity<Map<String, Object>> token(
            @RequestParam(value = "accessKey", required = false) String accessKey,
            @RequestParam(value = "timestamp", required = false) String timestamp,
            @RequestParam(value = "signature", required = false) String signature) {

        if (TokenCodec.isBlank(accessKey) || TokenCodec.isBlank(timestamp) || TokenCodec.isBlank(signature)) {
            return ResponseEntity.badRequest().body(error("accessKey、timestamp、signature 均不能为空"));
        }
        long ts;
        try {
            ts = Long.parseLong(timestamp.trim());
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(error("timestamp 必须为秒级时间戳"));
        }
        long tolerance = properties.getSignature().getTimestampToleranceSeconds();
        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - ts) > tolerance) {
            return ResponseEntity.badRequest().body(error("timestamp 超出允许偏差 " + tolerance + " 秒"));
        }

        Optional<CallerCredentialEntity> credential = credentials.enabledByAccessKey(accessKey.trim());
        if (credential.isEmpty()) {
            return ResponseEntity.status(401).body(error("accessKey 未登记或已停用"));
        }

        String secretKey;
        try {
            secretKey = credentials.decryptSecret(credential.get());
        } catch (Exception e) {
            return ResponseEntity.status(500).body(error("凭证解密失败"));
        }

        Map<String, String> params = new LinkedHashMap<>();
        params.put("accessKey", accessKey.trim());
        params.put("timestamp", timestamp.trim());
        String expected = "given".equalsIgnoreCase(properties.getSignature().getParamOrder())
                ? SignatureSupport.signInOrder(List.of("accessKey", "timestamp"), params, secretKey)
                : SignatureSupport.signSorted(params, secretKey);
        if (!SignatureSupport.matches(expected, signature)) {
            log.warn("签名校验失败 accessKey={}", accessKey.trim());
            return ResponseEntity.status(401).body(error("签名校验失败"));
        }

        String token = TokenCodec.generate();
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(properties.getToken().getTtlSeconds());
        issued.put(token, new IssuedToken(accessKey.trim(), expiresAt));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", token);
        data.put("issuedAt", issuedAt.toString());
        data.put("expiresAt", expiresAt.toString());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 0);
        body.put("msg", "成功");
        body.put("data", data);
        return ResponseEntity.ok(body);
    }

    /** 供业务鉴权使用。 */
    public Optional<String> resolveAccessKey(String token) {
        IssuedToken t = issued.get(token);
        if (t == null || t.expiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }
        return Optional.of(t.accessKey());
    }

    public int issuedTokenCount() {
        Instant now = Instant.now();
        issued.entrySet().removeIf(e -> e.getValue().expiresAt().isBefore(now));
        return issued.size();
    }

    private static Map<String, Object> error(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 1);
        body.put("msg", message);
        body.put("error", message);
        return body;
    }

    private record IssuedToken(String accessKey, Instant expiresAt) {
    }
}
