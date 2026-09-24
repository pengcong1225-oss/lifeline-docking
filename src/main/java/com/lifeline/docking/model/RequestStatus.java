package com.lifeline.docking.model;

/**
 * 新平台对一次业务请求的处理状态。
 *
 * <p>第一版至少区分收到、老平台已接受、老平台已拒绝、结果未知。
 * 结果未知时不允许伪装成功，也不允许自动重放。</p>
 */
public enum RequestStatus {

    /** 已可靠留存，尚未转发。 */
    RECEIVED,
    /** 老平台返回 code=0。 */
    ACCEPTED_OLD,
    /** 老平台返回非零业务码。 */
    REJECTED_OLD,
    /** 新平台自身拒绝（报文非法、路由缺字段等），未转发老平台。 */
    REJECTED_LOCAL,
    /** 老平台超时或断线，结果未知；不得自动重放，也不得报成功。 */
    UNKNOWN
}
