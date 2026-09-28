package com.equestrian.club.dto;

/**
 * 健康事件状态流转请求体（待处理 / 观察中 / 待复查 之间人工流转）。
 */
public class HealthTransitionRequest extends OperatorRequest {

    /** 目标状态：OBSERVING / REVIEW_PENDING 等，合法边由后端状态机把关 */
    private String targetStatus;

    /** 本次流转说明，必填 */
    private String note;

    /** 页面打开时看到的马匹健康事件链版本，落后即 409 */
    private Long expectedVersion;

    public String getTargetStatus() {
        return targetStatus;
    }

    public void setTargetStatus(String targetStatus) {
        this.targetStatus = targetStatus;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Long getExpectedVersion() {
        return expectedVersion;
    }

    public void setExpectedVersion(Long expectedVersion) {
        this.expectedVersion = expectedVersion;
    }
}
