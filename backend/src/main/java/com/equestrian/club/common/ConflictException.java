package com.equestrian.club.common;

/**
 * 并发冲突异常：提交时携带的数据版本（事件链版本 / 事件版本）已落后于库里的最新值，
 * 说明自己打开页面后有别人又登记了新伤病、做了复查或流转。
 *
 * <p>统一异常处理会转成 HTTP 409，前端据此提示「数据已变化，请重新载入最新事件链」，
 * 而不是让旧结论静默覆盖最新状态。
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
