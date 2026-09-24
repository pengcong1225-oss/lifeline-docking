package com.lifeline.docking.dto;

import com.lifeline.docking.entity.CallerCredentialEntity;

import java.time.LocalDateTime;

/** 凭证展示视图：绝不包含 secretKey 或密文，只给尾部提示。 */
public class CredentialView {

    private final Long id;
    private final String accessKey;
    private final String secretHint;
    private final String companyName;
    private final String contact;
    private final Boolean enabled;
    private final LocalDateTime verifiedAt;
    private final String verifyMessage;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public CredentialView(CallerCredentialEntity e) {
        this.id = e.getId();
        this.accessKey = e.getAccessKey();
        this.secretHint = e.getSecretHint();
        this.companyName = e.getCompanyName();
        this.contact = e.getContact();
        this.enabled = e.getEnabled();
        this.verifiedAt = e.getVerifiedAt();
        this.verifyMessage = e.getVerifyMessage();
        this.createdAt = e.getCreatedAt();
        this.updatedAt = e.getUpdatedAt();
    }

    public Long getId() { return id; }
    public String getAccessKey() { return accessKey; }
    public String getSecretHint() { return secretHint; }
    public String getCompanyName() { return companyName; }
    public String getContact() { return contact; }
    public Boolean getEnabled() { return enabled; }
    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public String getVerifyMessage() { return verifyMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
