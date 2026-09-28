package com.equestrian.club.dto;

/**
 * 补充当班交接总说明。只有该班次负责人（MANAGER）、且班次仍在进行中可改。
 */
public class ShiftNoteRequest extends OperatorRequest {

    /** 交接总说明，必填（覆盖式更新总说明，事项级补充仍走只追加流水） */
    private String handoverNote;

    /** 页面看到的班次清单版本，落后于库里当前版本直接 409 */
    private Long expectedVersion;

    public String getHandoverNote() {
        return handoverNote;
    }

    public void setHandoverNote(String handoverNote) {
        this.handoverNote = handoverNote;
    }

    public Long getExpectedVersion() {
        return expectedVersion;
    }

    public void setExpectedVersion(Long expectedVersion) {
        this.expectedVersion = expectedVersion;
    }
}
