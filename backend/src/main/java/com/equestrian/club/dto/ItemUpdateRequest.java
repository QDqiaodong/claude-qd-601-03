package com.equestrian.club.dto;

/**
 * 调整事项优先级 / 分派负责教练。只有来源班次负责人（MANAGER）可以操作。
 */
public class ItemUpdateRequest extends OperatorRequest {

    /** HIGH / NORMAL / LOW；不传表示不改 */
    private String priority;

    /** 负责教练姓名；传空串表示清除分派；不传（null）表示不改 */
    private String ownerName;

    /** 页面看到的事项版本，落后于库里当前版本直接 409 */
    private Long expectedVersion;

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public Long getExpectedVersion() {
        return expectedVersion;
    }

    public void setExpectedVersion(Long expectedVersion) {
        this.expectedVersion = expectedVersion;
    }
}
