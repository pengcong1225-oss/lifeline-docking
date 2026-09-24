package com.lifeline.docking.service;

import com.lifeline.docking.client.OldPlatformClient;
import com.lifeline.docking.config.DockingProperties;
import com.lifeline.docking.entity.CallerCredentialEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 老平台令牌的本地缓存。
 *
 * <p>新平台用<b>该调用者自己的老凭证</b>在内部向老平台取令牌，缓存后用于转发；
 * 老令牌只在新平台内部使用，绝不返回给调用者。</p>
 *
 * <p><b>阻塞项</b>：源文档没有给出老令牌的真实有效期，这里按保守的固定窗口缓存，
 * 联调确认后再调整 {@code lifeline.token.old-token-cache-seconds}。</p>
 */
@Service
public class OldPlatformTokenService {

    private static final Logger log = LoggerFactory.getLogger(OldPlatformTokenService.class);

    private final OldPlatformClient client;
    private final CredentialService credentials;
    private final DockingProperties properties;
    private final Map<String, CachedToken> cache = new ConcurrentHashMap<>();

    public OldPlatformTokenService(OldPlatformClient client,
                                   CredentialService credentials,
                                   DockingProperties properties) {
        this.client = client;
        this.credentials = credentials;
        this.properties = properties;
    }

    /** 取可用老令牌：命中缓存直接返回，否则重新获取。 */
    public OldPlatformClient.TokenResult getToken(String accessKey) {
        CachedToken cached = cache.get(accessKey);
        if (cached != null && cached.isFresh(properties.getToken().getOldTokenRefreshAheadSeconds())) {
            return OldPlatformClient.TokenResult.ok(cached.token(), cached.rawBody());
        }
        return refresh(accessKey);
    }

    /** 强制重新获取老令牌。 */
    public OldPlatformClient.TokenResult refresh(String accessKey) {
        Optional<CallerCredentialEntity> credential = credentials.enabledByAccessKey(accessKey);
        if (credential.isEmpty()) {
            return OldPlatformClient.TokenResult.fail("调用者凭证未登记或已停用", null);
        }
        String secret;
        try {
            secret = credentials.decryptSecret(credential.get());
        } catch (Exception e) {
            return OldPlatformClient.TokenResult.fail("secretKey 解密失败", null);
        }
        OldPlatformClient.TokenResult result = client.fetchToken(
                accessKey, secret, String.valueOf(Instant.now().getEpochSecond()));
        if (result.ok()) {
            long ttl = properties.getToken().getOldTokenCacheSeconds();
            cache.put(accessKey, new CachedToken(result.token(), result.rawBody(),
                    Instant.now().plusSeconds(ttl)));
            log.info("已获取并缓存老平台令牌 accessKey={} 缓存 {} 秒", accessKey, ttl);
        } else {
            cache.remove(accessKey);
            log.warn("获取老平台令牌失败 accessKey={} message={}", accessKey, result.message());
        }
        return result;
    }

    /** 老平台判定令牌失效或业务拒绝时调用，下次请求会重新取令牌。 */
    public void invalidate(String accessKey) {
        cache.remove(accessKey);
    }

    public boolean isCached(String accessKey) {
        CachedToken cached = cache.get(accessKey);
        return cached != null && cached.isFresh(0);
    }

    private record CachedToken(String token, String rawBody, Instant expiresAt) {
        boolean isFresh(long refreshAheadSeconds) {
            return Instant.now().plusSeconds(refreshAheadSeconds).isBefore(expiresAt);
        }
    }
}
