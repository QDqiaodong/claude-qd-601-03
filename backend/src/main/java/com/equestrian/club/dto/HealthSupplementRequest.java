package com.equestrian.club.dto;

/**
 * 补充处置请求体：不改动既有事件字段与历史，只追加一条 SUPPLEMENT 日志。
 */
public class HealthSupplementRequest extends OperatorRequest {

    /** 补充的处置说明，必填 */
    private String note;

    private Long expectedVersion;

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
