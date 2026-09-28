package com.equestrian.club.dto;

/**
 * 复训放行确认请求体（负责人专用，后端强制 MANAGER 角色）。
 */
public class HealthReleaseRequest extends OperatorRequest {

    /** 页面打开时看到的马匹健康事件链版本，落后即 409 */
    private Long expectedVersion;

    /** 放行说明，可空 */
    private String note;

    public Long getExpectedVersion() {
        return expectedVersion;
    }

    public void setExpectedVersion(Long expectedVersion) {
        this.expectedVersion = expectedVersion;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
