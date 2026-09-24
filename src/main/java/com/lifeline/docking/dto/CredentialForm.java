package com.lifeline.docking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 企业登记老凭证的入参。 */
public class CredentialForm {

    @NotBlank(message = "accessKey 不能为空")
    @Size(max = 128, message = "accessKey 过长")
    private String accessKey;

    /** 仅提交时使用；不落库明文，不回显。 */
    @NotBlank(message = "secretKey 不能为空")
    @Size(max = 512, message = "secretKey 过长")
    private String secretKey;

    @Size(max = 128, message = "企业名称过长")
    private String companyName;

    @Size(max = 64, message = "联系人过长")
    private String contact;

    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }
    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }
}
