package com.lifeline.docking.web.admin;

import com.lifeline.docking.dto.CredentialForm;
import com.lifeline.docking.dto.CredentialView;
import com.lifeline.docking.entity.CallerCredentialEntity;
import com.lifeline.docking.service.CredentialService;
import com.lifeline.docking.service.OldPlatformTokenService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 凭证登记与管理接口（供企业录入页使用）。
 *
 * <p>响应中永不出现 secretKey 明文或密文，只回显尾部提示。</p>
 */
@RestController
@RequestMapping("/admin/api/credentials")
public class CredentialAdminController {

    private final CredentialService credentialService;
    private final OldPlatformTokenService oldTokenService;

    public CredentialAdminController(CredentialService credentialService,
                                     OldPlatformTokenService oldTokenService) {
        this.credentialService = credentialService;
        this.oldTokenService = oldTokenService;
    }

    @GetMapping
    public List<CredentialView> list() {
        return credentialService.list();
    }

    @PostMapping
    public CredentialView create(@Valid @RequestBody CredentialForm form) {
        return credentialService.register(form);
    }

    @PutMapping("/{id}")
    public CredentialView update(@PathVariable Long id, @Valid @RequestBody CredentialForm form) {
        CallerCredentialEntity existing = credentialService.byId(id)
                .orElseThrow(() -> new IllegalArgumentException("凭证不存在：" + id));
        // accessKey 不允许通过更新改掉，避免与已发放令牌的归属错位。
        form.setAccessKey(existing.getAccessKey());
        return credentialService.register(form);
    }

    @PostMapping("/{id}/enabled")
    public Map<String, Object> setEnabled(@PathVariable Long id, @RequestParam boolean value) {
        credentialService.setEnabled(id, value);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("enabled", value);
        return result;
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        credentialService.byId(id).ifPresent(e -> oldTokenService.invalidate(e.getAccessKey()));
        credentialService.delete(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deleted", id);
        return result;
    }

    /**
     * 向老平台实际取一次令牌来验证凭证。
     *
     * <p>这是唯一会主动联系老平台的凭证操作，仅在已获得联调授权时使用。</p>
     */
    @PostMapping("/{id}/verify")
    public ResponseEntity<Map<String, Object>> verify(@PathVariable Long id) {
        CredentialService.VerifyOutcome outcome = credentialService.verify(id);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", outcome.ok());
        body.put("message", outcome.message());
        return outcome.ok() ? ResponseEntity.ok(body) : ResponseEntity.status(502).body(body);
    }
}
