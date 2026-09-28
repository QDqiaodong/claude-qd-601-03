package com.equestrian.club.domain;

import java.math.BigDecimal;
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
 * 骑乘记录。业务规则：必须关联一条已排期的课程场次；
 * 同一会员同一天同一时段不可重复预约。
 */
@Entity
@Table(name = "riding_record")
public class RidingRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    /** 关联的课程排期，必填 */
    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    /** 可空外键：实际使用的马匹 */
    @Column(name = "horse_id")
    private Long horseId;

    @Column(name = "record_date", nullable = false)
    private LocalDate recordDate;

    @Column(name = "start_time", nullable = false, length = 5)
    private String startTime;

    @Column(name = "end_time", nullable = false, length = 5)
    private String endTime;

    /** BOOKED 已预约 / COMPLETED 已完成 / CANCELED 已取消 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** 实际扣费金额（已按会员折扣折算） */
    @Column(name = "fee", nullable = false)
    private BigDecimal fee;

    /** 实际扣减的会员卡次数 */
    @Column(name = "times_used", nullable = false)
    private Integer timesUsed;

    @Column(name = "remark", length = 255)
    private String remark;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
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

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public Long getHorseId() {
        return horseId;
    }

    public void setHorseId(Long horseId) {
        this.horseId = horseId;
    }

    public LocalDate getRecordDate() {
        return recordDate;
    }

    public void setRecordDate(LocalDate recordDate) {
        this.recordDate = recordDate;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getFee() {
        return fee;
    }

    public void setFee(BigDecimal fee) {
        this.fee = fee;
    }

    public Integer getTimesUsed() {
        return timesUsed;
    }

    public void setTimesUsed(Integer timesUsed) {
        this.timesUsed = timesUsed;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
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
