package com.equestrian.club.dto;

import java.time.LocalDate;

/**
 * 建立值班班次请求：只有值班负责人（MANAGER）能建立，角色由后端强制校验。
 */
public class ShiftCreateRequest extends OperatorRequest {

    /** 值班日期；不传取今天 */
    private LocalDate shiftDate;

    /** 开班交接总说明，可空 */
    private String handoverNote;

    public LocalDate getShiftDate() {
        return shiftDate;
    }

    public void setShiftDate(LocalDate shiftDate) {
        this.shiftDate = shiftDate;
    }

    public String getHandoverNote() {
        return handoverNote;
    }

    public void setHandoverNote(String handoverNote) {
        this.handoverNote = handoverNote;
    }
}
