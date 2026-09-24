package com.lifeline.docking.catalog;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 一个接口条目。18 个业务操作共用 {@code POST /data-docking-api/execute/api}，
 * 必须靠 apiCmd + tag + operationType 区分。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiCatalogEntry {

    private int seq;
    private String category;
    private String name;
    private String method;
    private String path;
    private String contentType;
    private String apiCmd;
    private String tag;
    private String operationType;
    /** TOKEN 或 BUSINESS。 */
    private String kind;
    private List<ApiField> queryParams = new ArrayList<>();
    private List<ApiField> dataFields = new ArrayList<>();
    private List<ApiField> responseFields = new ArrayList<>();

    public boolean isToken() {
        return "TOKEN".equalsIgnoreCase(kind);
    }

    /** 路由键展示，例如 {@code lifeline_data_batch_acces / construction_info}。 */
    public String getRouteLabel() {
        if (isToken()) {
            return path;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(apiCmd).append(" / ").append(tag);
        if (operationType != null && !operationType.isBlank()) {
            sb.append(" / ").append(operationType);
        }
        return sb.toString();
    }

    public int getSeq() { return seq; }
    public void setSeq(int seq) { this.seq = seq; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public String getApiCmd() { return apiCmd; }
    public void setApiCmd(String apiCmd) { this.apiCmd = apiCmd; }
    public String getTag() { return tag; }
    public void setTag(String tag) { this.tag = tag; }
    public String getOperationType() { return operationType; }
    public void setOperationType(String operationType) { this.operationType = operationType; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public List<ApiField> getQueryParams() { return queryParams; }
    public void setQueryParams(List<ApiField> queryParams) { this.queryParams = queryParams; }
    public List<ApiField> getDataFields() { return dataFields; }
    public void setDataFields(List<ApiField> dataFields) { this.dataFields = dataFields; }
    public List<ApiField> getResponseFields() { return responseFields; }
    public void setResponseFields(List<ApiField> responseFields) { this.responseFields = responseFields; }
}
