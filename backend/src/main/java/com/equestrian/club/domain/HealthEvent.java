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
 * 马匹健康事件主实体。
 *
 * <p>闭环状态机：{@code PENDING 待处理 -> OBSERVING 观察中 -> REVIEW_PENDING 待复查 -> CLOSED 已关闭}。
 * CLOSED 是终态，且只能由负责人复训放行时写入，普通工作人员没有关单入口。
 *
 * <p>发生过的处置历史不在本实体上做覆盖式更新：每次操作都追加一条
 * {@link HealthEventLog}，每次复查追加一条 {@link HealthReview}，历史只增不改。
 */
@Entity
@Table(name = "health_event")
public class HealthEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 事件编号，唯一，形如 HE-000007 */
    @Column(name = "event_no", nullable = false, unique = true, length = 32)
    private String eventNo;

    @Column(name = "horse_id", nullable = false)
    private Long horseId;

    /** 发生时间 */
    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    /** HIGH 高风险 / MEDIUM 中风险 / LOW 低风险；只有 HIGH 会立即强制休养并冻结排课 */
    @Column(name = "severity", nullable = false, length = 16)
    private String severity;

    @Column(name = "symptom", nullable = false, length = 500)
    private String symptom;

    @Column(name = "treatment_advice", nullable = false, length = 500)
    private String treatmentAdvice;

    /** 预计复查日 */
    @Column(name = "expected_review_date")
    private LocalDate expectedReviewDate;

    /** PENDING / OBSERVING / REVIEW_PENDING / CLOSED */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /**
     * 事件登记落定后马匹事件链的版本号。放行时用它判断「复查是否晚于最新伤病事件」，
     * 是单调的版本序，不依赖秒级时间戳。
     */
    @Column(name = "registered_version", nullable = false)
    private Long registeredVersion = 0L;

    /** 最近一次复查记录 id（马匹详情「最近一次复查结论」直接读） */
    @Column(name = "latest_review_id")
    private Long latestReviewId;

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;

    /** STAFF / MANAGER */
    @Column(name = "created_role", nullable = false, length = 16)
    private String createdRole;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "closed_by", length = 64)
    private String closedBy;

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

    public String getEventNo() {
        return eventNo;
    }

    public void setEventNo(String eventNo) {
        this.eventNo = eventNo;
    }

    public Long getHorseId() {
        return horseId;
    }

    public void setHorseId(Long horseId) {
        this.horseId = horseId;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getSymptom() {
        return symptom;
    }

    public void setSymptom(String symptom) {
        this.symptom = symptom;
    }

    public String getTreatmentAdvice() {
        return treatmentAdvice;
    }

    public void setTreatmentAdvice(String treatmentAdvice) {
        this.treatmentAdvice = treatmentAdvice;
    }

    public LocalDate getExpectedReviewDate() {
        return expectedReviewDate;
    }

    public void setExpectedReviewDate(LocalDate expectedReviewDate) {
        this.expectedReviewDate = expectedReviewDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getRegisteredVersion() {
        return registeredVersion;
    }

    public void setRegisteredVersion(Long registeredVersion) {
        this.registeredVersion = registeredVersion;
    }

    public Long getLatestReviewId() {
        return latestReviewId;
    }

    public void setLatestReviewId(Long latestReviewId) {
        this.latestReviewId = latestReviewId;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getCreatedRole() {
        return createdRole;
    }

    public void setCreatedRole(String createdRole) {
        this.createdRole = createdRole;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(LocalDateTime closedAt) {
        this.closedAt = closedAt;
    }

    public String getClosedBy() {
        return closedBy;
    }

    public void setClosedBy(String closedBy) {
        this.closedBy = closedBy;
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
