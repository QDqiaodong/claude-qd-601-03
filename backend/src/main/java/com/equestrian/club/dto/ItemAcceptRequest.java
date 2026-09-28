package com.equestrian.club.dto;

/**
 * 下一班负责人逐项确认承接。
 * 角色必须是 MANAGER，且操作人必须是目标班次负责人；普通教练不能确认承接。
 * 重复提交（同一 requestId）只取回同一条承接记录，绝不产生重复承接。
 */
public class ItemAcceptRequest extends OperatorRequest {

    /** 承接说明，可空 */
    private String note;

    /** 页面看到的事项版本，落后于库里当前版本直接 409 */
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
