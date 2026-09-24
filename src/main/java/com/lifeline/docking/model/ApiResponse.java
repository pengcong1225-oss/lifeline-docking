package com.lifeline.docking.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/**
 * 兼容老平台的业务响应：{@code code / msg / error / data}。
 *
 * <p>业务成功判定只看 {@code code=0}；HTTP 200 本身不代表成功，{@code data=true} 也不代表成功。</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse {

    /** 成功标记。 */
    public static final int CODE_SUCCESS = 0;
    /** 失败标记。 */
    public static final int CODE_FAILURE = 1;

    private int code;
    private String msg;
    private String error;
    private Object data;

    public static ApiResponse success(Object data) {
        ApiResponse r = new ApiResponse();
        r.code = CODE_SUCCESS;
        r.msg = "成功";
        r.data = data;
        return r;
    }

    public static ApiResponse failure(String message) {
        ApiResponse r = new ApiResponse();
        r.code = CODE_FAILURE;
        r.msg = message;
        r.error = message;
        return r;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> dataAsMap() {
        return data instanceof Map ? (Map<String, Object>) data : null;
    }

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }
    public String getMsg() { return msg; }
    public void setMsg(String msg) { this.msg = msg; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error; }
    public Object getData() { return data; }
    public void setData(Object data) { this.data = data; }
}
