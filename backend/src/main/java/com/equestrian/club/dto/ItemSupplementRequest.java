package com.equestrian.club.dto;

/**
 * 补充事项说明。
 * 普通教练（STAFF）只能补充「分派给自己」的事项；负责人可补充本班任意未承接事项。
 * 补充只追加流水，不覆盖既有标题 / 快照 / 历史。
 */
public class ItemSupplementRequest extends OperatorRequest {

    /** 补充说明，必填 */
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
