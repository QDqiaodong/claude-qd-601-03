package com.equestrian.club.domain;

import java.time.LocalDate;
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
 * 值班班次：值班负责人建立，OPEN 进行中 -> CLOSED 已结束（终态）。
 *
 * <p>交接期间允许新旧两个 OPEN 班次并存（下一班先建立、当班再结束）。
 * {@code itemVersion} 是班次交接清单的乐观锁版本：事项分派 / 新事项挂入 / 关班都 +1，
 * 旧页面拿旧版本提交直接 409，提示重新载入。
 */
@Entity
@Table(name = "duty_shift")
public class DutyShift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 班次编号，唯一，形如 DS-000001 */
    @Column(name = "shift_no", nullable = false, unique = true, length = 32)
    private String shiftNo;

    @Column(name = "shift_date", nullable = false)
    private LocalDate shiftDate;

    /** 值班负责人姓名（关班 / 改交接说明 / 分派事项都必须是本人） */
    @Column(name = "leader_name", nullable = false, length = 64)
    private String leaderName;

    /** 当班交接总说明（可多次补充） */
    @Column(name = "handover_note", length = 1000)
    private String handoverNote;

    /** OPEN 进行中 / CLOSED 已结束 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** 交接清单版本（乐观锁） */
    @Column(name = "item_version", nullable = false)
    private Long itemVersion = 0L;

    /** 开班请求幂等键（重复点击只取回同一个班次） */
    @Column(name = "create_request_id", unique = true, length = 64)
    private String createRequestId;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

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

    public String getShiftNo() {
        return shiftNo;
    }

    public void setShiftNo(String shiftNo) {
        this.shiftNo = shiftNo;
    }

    public LocalDate getShiftDate() {
        return shiftDate;
    }

    public void setShiftDate(LocalDate shiftDate) {
        this.shiftDate = shiftDate;
    }

    public String getLeaderName() {
        return leaderName;
    }

    public void setLeaderName(String leaderName) {
        this.leaderName = leaderName;
    }

    public String getHandoverNote() {
        return handoverNote;
    }

    public void setHandoverNote(String handoverNote) {
        this.handoverNote = handoverNote;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getItemVersion() {
        return itemVersion;
    }

    public void setItemVersion(Long itemVersion) {
        this.itemVersion = itemVersion;
    }

    public String getCreateRequestId() {
        return createRequestId;
    }

    public void setCreateRequestId(String createRequestId) {
        this.createRequestId = createRequestId;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(LocalDateTime closedAt) {
        this.closedAt = closedAt;
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
