package com.lifeline.docking.service;

import com.lifeline.docking.client.OldPlatformClient;
import com.lifeline.docking.crypto.SecretCipher;
import com.lifeline.docking.dto.CredentialForm;
import com.lifeline.docking.dto.CredentialView;
import com.lifeline.docking.entity.CallerCredentialEntity;
import com.lifeline.docking.mapper.CallerCredentialMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 调用者老凭证的登记与管理。
 *
 * <p>企业把自己在老平台的 accessKey/secretKey 登记到新平台；secretKey 立即加密，
 * 只在需要签名或取老令牌时于内存中解密。任何日志、接口响应都不出现明文或密文。</p>
 */
@Service
public class CredentialService {

    private static final Logger log = LoggerFactory.getLogger(CredentialService.class);

    private final CallerCredentialMapper mapper;
    private final SecretCipher cipher;
    private final OldPlatformClient oldPlatformClient;

    public CredentialService(CallerCredentialMapper mapper,
                             SecretCipher cipher,
                             OldPlatformClient oldPlatformClient) {
        this.mapper = mapper;
        this.cipher = cipher;
        this.oldPlatformClient = oldPlatformClient;
    }

    /** 登记或按 accessKey 覆盖更新。 */
    public CredentialView register(CredentialForm form) {
        String accessKey = form.getAccessKey().trim();
        String secretKey = form.getSecretKey().trim();
        Optional<CallerCredentialEntity> existing = rawByAccessKey(accessKey);

        LocalDateTime now = LocalDateTime.now();
        CallerCredentialEntity entity = existing.orElseGet(CallerCredentialEntity::new);
        entity.setAccessKey(accessKey);
        entity.setSecretCipher(cipher.encrypt(secretKey));
        entity.setSecretHint(SecretCipher.hint(secretKey));
        entity.setCompanyName(nullToEmpty(form.getCompanyName()));
        entity.setContact(nullToEmpty(form.getContact()));
        entity.setUpdatedAt(now);
        if (entity.getId() == null) {
            entity.setEnabled(true);
            entity.setVerifyMessage("");
            entity.setCreatedAt(now);
            mapper.insert(entity);
        } else {
            mapper.updateById(entity);
        }
        // 只记录 accessKey 与尾号，绝不记录明文密钥。
        log.info("已登记凭证 accessKey={} hint={}", accessKey, entity.getSecretHint());
        return new CredentialView(entity);
    }

    public List<CredentialView> list() {
        return mapper.selectList(new QueryWrapper<CallerCredentialEntity>()
                        .orderByDesc("updated_at"))
                .stream().map(CredentialView::new).toList();
    }

    public Optional<CallerCredentialEntity> byId(Long id) {
        return Optional.ofNullable(mapper.selectById(id));
    }

    public Optional<CallerCredentialEntity> byAccessKey(String accessKey) {
        return rawByAccessKey(accessKey);
    }

    /** 取可用的（已启用）凭证。 */
    public Optional<CallerCredentialEntity> enabledByAccessKey(String accessKey) {
        return rawByAccessKey(accessKey).filter(e -> Boolean.TRUE.equals(e.getEnabled()));
    }

    private Optional<CallerCredentialEntity> rawByAccessKey(String accessKey) {
        if (accessKey == null || accessKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(mapper.selectOne(
                new QueryWrapper<CallerCredentialEntity>().eq("access_key", accessKey.trim())));
    }

    public void setEnabled(Long id, boolean enabled) {
        CallerCredentialEntity entity = mapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("凭证不存在：" + id);
        }
        entity.setEnabled(enabled);
        entity.setUpdatedAt(LocalDateTime.now());
        mapper.updateById(entity);
    }

    public void delete(Long id) {
        mapper.deleteById(id);
    }

    /** 解密 secretKey 供内部签名使用。 */
    public String decryptSecret(CallerCredentialEntity entity) {
        return cipher.decrypt(entity.getSecretCipher());
    }

    /**
     * 向老平台实际取一次令牌，验证凭证是否可用。
     *
     * <p>这是唯一会主动联系老平台的凭证操作；调用它需要明确的联调授权。</p>
     */
    public VerifyOutcome verify(Long id) {
        CallerCredentialEntity entity = mapper.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("凭证不存在：" + id);
        }
        String secret;
        try {
            secret = cipher.decrypt(entity.getSecretCipher());
        } catch (Exception e) {
            return recordVerify(entity, false, "解密失败：主密钥可能已更换");
        }
        OldPlatformClient.TokenResult result = oldPlatformClient.fetchToken(
                entity.getAccessKey(), secret, String.valueOf(Instant.now().getEpochSecond()));
        return recordVerify(entity, result.ok(), result.message());
    }

    private VerifyOutcome recordVerify(CallerCredentialEntity entity, boolean ok, String message) {
        LocalDateTime now = LocalDateTime.now();
        entity.setVerifyMessage(truncate(message, 500));
        entity.setUpdatedAt(now);
        if (ok) {
            entity.setVerifiedAt(now);
        }
        mapper.updateById(entity);
        return new VerifyOutcome(ok, message);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** 验证结果。 */
    public record VerifyOutcome(boolean ok, String message) {
    }
}
