package com.lifeline.docking.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 业务请求留存。 */
@TableName("docking_request_record")
public class RequestRecordEntity {

    @TableId(type = IdType.INPUT)
    private String recordId;
    private String accessKey;
    private String apiCmd;
    private String tag;
    private String operationType;
    private Integer dataCount;
    private Integer storedCount;
    private Integer duplicateCount;
    private String rawBody;
    private String status;
    private String message;
    private Integer oldPlatformCode;
    private String oldResponse;
    private String source;
    private LocalDateTime receivedAt;
    private LocalDateTime forwardedAt;

    public String getRecordId() { return recordId; }
    public void setRecordId(String recordId) { this.recordId = recordId; }
    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
    public String getApiCmd() { return apiCmd; }
    public void setApiCmd(String apiCmd) { this.apiCmd = apiCmd; }
    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }
    public String getOperationType() { return operationType; }
    public void setOperationType(String operationType) { this.operationType = operationType; }
    public Integer getDataCount() { return dataCount; }
    public void setDataCount(Integer dataCount) { this.dataCount = dataCount; }
    public Integer getStoredCount() { return storedCount; }
    public void setStoredCount(Integer storedCount) { this.storedCount = storedCount; }
    public Integer getDuplicateCount() { return duplicateCount; }
    public void setDuplicateCount(Integer duplicateCount) { this.duplicateCount = duplicateCount; }
    public String getRawBody() { return rawBody; }
    public void setRawBody(String rawBody) { this.rawBody = rawBody; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public Integer getOldPlatformCode() { return oldPlatformCode; }
    public void setOldPlatformCode(Integer oldPlatformCode) { this.oldPlatformCode = oldPlatformCode; }
    public String getOldResponse() { return oldResponse; }
    public void setOldResponse(String oldResponse) { this.oldResponse = oldResponse; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public LocalDateTime getReceivedAt() { return receivedAt; }
    public void setReceivedAt(LocalDateTime receivedAt) { this.receivedAt = receivedAt; }
    public LocalDateTime getForwardedAt() { return forwardedAt; }
    public void setForwardedAt(LocalDateTime forwardedAt) { this.forwardedAt = forwardedAt; }
}
