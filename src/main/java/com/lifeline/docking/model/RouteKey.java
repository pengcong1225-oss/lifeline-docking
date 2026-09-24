package com.lifeline.docking.model;

import java.util.Objects;

/**
 * 业务路由键：apiCmd + tag + operationType（operationType 可为空）。
 */
public final class RouteKey {

    private final String apiCmd;
    private final String tag;
    /** 为空表示该接口未定义操作类型。 */
    private final String operationType;

    private RouteKey(String apiCmd, String tag, String operationType) {
        this.apiCmd = apiCmd;
        this.tag = tag;
        this.operationType = operationType == null ? "" : operationType;
    }

    public static RouteKey of(String apiCmd, String tag) {
        return new RouteKey(apiCmd, tag, "");
    }

    public static RouteKey of(String apiCmd, String tag, String operationType) {
        return new RouteKey(apiCmd, tag, operationType);
    }

    public String getApiCmd() { return apiCmd; }
    public String getTag() { return tag; }
    public String getOperationType() { return operationType; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RouteKey other)) return false;
        return apiCmd.equals(other.apiCmd)
                && Objects.equals(tag, other.tag)
                && operationType.equals(other.operationType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(apiCmd, tag, operationType);
    }

    @Override
    public String toString() {
        return apiCmd + "/" + tag + (operationType.isEmpty() ? "" : ":" + operationType);
    }
}
