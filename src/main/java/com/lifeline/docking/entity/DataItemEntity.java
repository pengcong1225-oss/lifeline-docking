package com.lifeline.docking.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/** 上收的业务数据项。 */
@TableName("docking_data_item")
public class DataItemEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String recordId;
    private String accessKey;
    private String apiCmd;
    private String tag;
    private String operationType;
    private Integer itemIndex;
    /** 流水号；老平台按此做唯一校验。部分接口（第三方巡检）无此字段，为 null。 */
    private String lsh;
    /** 原始数据项 JSON 文本，落库到 JSON 列。 */
    private String payload;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
    public Integer getItemIndex() { return itemIndex; }
    public void setItemIndex(Integer itemIndex) { this.itemIndex = itemIndex; }
    public String getLsh() { return lsh; }
    public void setLsh(String lsh) { this.lsh = lsh; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
