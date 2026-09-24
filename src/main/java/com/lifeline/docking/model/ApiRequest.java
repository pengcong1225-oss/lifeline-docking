package com.lifeline.docking.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.Map;

/**
 * 业务请求统一模型：18 个业务操作共用 {@code POST /data-docking-api/execute/api}，
 * 依靠 apiCmd + apiBody.tag + apiBody.operationType 区分，路由不能只看 URL。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ApiRequest {

    @NotBlank(message = "apiCmd 不能为空")
    private String apiCmd;

    private ApiBody apiBody;

    public String getApiCmd() { return apiCmd; }
    public void setApiCmd(String apiCmd) { this.apiCmd = apiCmd; }
    public ApiBody getApiBody() { return apiBody; }
    public void setApiBody(ApiBody apiBody) { this.apiBody = apiBody; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ApiBody {
        private String tag;
        /** 仅部分接口定义：I=新增或更新，D=删除。 */
        private String operationType;
        private List<Map<String, Object>> data;

        public String getTag() { return tag; }
        public void setTag(String tag) { this.tag = tag; }
        public String getOperationType() { return operationType; }
        public void setOperationType(String operationType) { this.operationType = operationType; }
        public List<Map<String, Object>> getData() { return data; }
        public void setData(List<Map<String, Object>> data) { this.data = data; }
    }
}
