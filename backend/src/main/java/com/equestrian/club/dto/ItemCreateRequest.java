package com.equestrian.club.dto;

/**
 * 手工补充交接事项（值班负责人）。
 */
public class ItemCreateRequest extends OperatorRequest {

    /** 事项标题，必填 */
    private String title;

    /** 事项说明 */
    private String detail;

    /** HIGH / NORMAL / LOW，不传默认 NORMAL */
    private String priority;

    /** 负责教练姓名，可空 */
    private String ownerName;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

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
}
