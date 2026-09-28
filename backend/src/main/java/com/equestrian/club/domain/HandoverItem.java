package com.equestrian.club.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * 值班交接事项（未结事项）。
 *
 * <p>状态机：{@code PENDING 待承接 -> ACCEPTED 已承接}（终态）。承接只由目标班次负责人
 * 逐项确认；已承接事项不能被旧班次改回未处理、不能再被补充 / 改优先级 / 分派。
 *
 * <p>未承接事项在来源班次结束后靠 {@code targetShiftId} 继续挂在承接班次首页，
 * 不会因班次结束而消失。关班时仍未承接的，必须逐项记录 {@code delayReason} 延期原因。
 *
 * <p>未承接事项的业务唯一键（shift_id + ref_type + ref_id + change_kind）保证：
 * 开班快照与期间课程变更实时挂入对同一业务对象只生成一条未承接事项。
 */
@Entity
@Table(name = "handover_item")
public class HandoverItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 事项编号，唯一，形如 HI-000001 */
    @Column(name = "item_no", nullable = false, unique = true, length = 32)
    private String itemNo;

    /** 来源班次：事项由哪个班次产生 */
    @Column(name = "shift_id", nullable = false)
    private Long shiftId;

    /** 承接班次：分派给哪个班次；null 表示尚未分派（留在本班本版处理） */
    @Column(name = "target_shift_id")
    private Long targetShiftId;

    /** SESSION 课程 / HORSE 马匹 / STALL 栏位 / MANUAL 手工 */
    @Column(name = "item_type", nullable = false, length = 24)
    private String itemType;

    /** SESSION_CHANGE / HORSE_STATUS / STALL_MAINT / MANUAL */
    @Column(name = "ref_type", length = 24)
    private String refType;

    /** 关联对象 id（排期 / 马匹 / 栏位），手工事项为 null */
    @Column(name = "ref_id")
    private Long refId;

    /** NEW 新排期 / CANCELED 取消 / AFFECTED 健康受影响 / FULL 已满员 */
    @Column(name = "change_kind", length = 24)
    private String changeKind;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /** 开班定格的快照说明 */
    @Column(name = "detail", length = 1000)
    private String detail;

    /** HIGH 高 / NORMAL 普通 / LOW 低 */
    @Column(name = "priority", nullable = false, length = 16)
    private String priority;

    /** 负责教练（普通教练只能补充分派给自己的事项）；null 表示未分派负责人 */
    @Column(name = "owner_name", length = 64)
    private String ownerName;

    /** PENDING 待承接 / ACCEPTED 已承接（终态） */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** 事项自身版本：补充 / 改优先级 / 分派 / 承接的乐观锁，提交旧版本直接 409 */
    @Column(name = "expected_version", nullable = false)
    private Long expectedVersion = 0L;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    /** 承接人（目标班次负责人） */
    @Column(name = "accepted_by", length = 64)
    private String acceptedBy;

    @Column(name = "accepted_note", length = 500)
    private String acceptedNote;

    /** 延期原因（结班时仍未承接，逐项填写） */
    @Column(name = "delay_reason", length = 500)
    private String delayReason;

    @Column(name = "delayed_at")
    private LocalDateTime delayedAt;

    @Column(name = "delayed_by", length = 64)
    private String delayedBy;

    /** 事项产生时的幂等键 */
    @Column(name = "request_id", length = 64)
    private String requestId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getItemNo() {
        return itemNo;
    }

    public void setItemNo(String itemNo) {
        this.itemNo = itemNo;
    }

    public Long getShiftId() {
        return shiftId;
    }

    public void setShiftId(Long shiftId) {
        this.shiftId = shiftId;
    }

    public Long getTargetShiftId() {
        return targetShiftId;
    }

    public void setTargetShiftId(Long targetShiftId) {
        this.targetShiftId = targetShiftId;
    }

    public String getItemType() {
        return itemType;
    }

    public void setItemType(String itemType) {
        this.itemType = itemType;
    }

    public String getRefType() {
        return refType;
    }

    public void setRefType(String refType) {
        this.refType = refType;
    }

    public Long getRefId() {
        return refId;
    }

    public void setRefId(Long refId) {
        this.refId = refId;
    }

    public String getChangeKind() {
        return changeKind;
    }

    public void setChangeKind(String changeKind) {
        this.changeKind = changeKind;
    }

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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getExpectedVersion() {
        return expectedVersion;
    }

    public void setExpectedVersion(Long expectedVersion) {
        this.expectedVersion = expectedVersion;
    }

    public LocalDateTime getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(LocalDateTime acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public String getAcceptedBy() {
        return acceptedBy;
    }

    public void setAcceptedBy(String acceptedBy) {
        this.acceptedBy = acceptedBy;
    }

    public String getAcceptedNote() {
        return acceptedNote;
    }

    public void setAcceptedNote(String acceptedNote) {
        this.acceptedNote = acceptedNote;
    }

    public String getDelayReason() {
        return delayReason;
    }

    public void setDelayReason(String delayReason) {
        this.delayReason = delayReason;
    }

    public LocalDateTime getDelayedAt() {
        return delayedAt;
    }

    public void setDelayedAt(LocalDateTime delayedAt) {
        this.delayedAt = delayedAt;
    }

    public String getDelayedBy() {
        return delayedBy;
    }

    public void setDelayedBy(String delayedBy) {
        this.delayedBy = delayedBy;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
