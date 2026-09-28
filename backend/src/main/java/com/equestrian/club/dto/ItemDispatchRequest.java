package com.equestrian.club.dto;

/**
 * 把事项分派给承接班次（下一班）。只有来源班次负责人（MANAGER）可以操作。
 *
 * <p>不传 targetShiftId 时后端取「最新一个晚于来源班次的 OPEN 班次」；
 * 已分派给同一目标班次的重复提交直接幂等返回。
 */
public class ItemDispatchRequest extends OperatorRequest {

    /** 指定承接班次；不传取当前最新的下一班 */
    private Long targetShiftId;

    /** 分派说明，可空 */
    private String note;

    /** 页面看到的事项版本，落后于库里当前版本直接 409 */
    private Long expectedVersion;

    public Long getTargetShiftId() {
        return targetShiftId;
    }

    public void setTargetShiftId(Long targetShiftId) {
        this.targetShiftId = targetShiftId;
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
