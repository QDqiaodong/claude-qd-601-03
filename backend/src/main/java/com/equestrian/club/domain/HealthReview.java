package com.equestrian.club.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * 健康事件复查记录：一次复查一条，只追加不改写。
 *
 * <p>{@code result} 三种：
 * <ul>
 *   <li>OBSERVE 继续观察：事件退回观察中，{@code nextReviewDate} 必填</li>
 *   <li>RESCHEDULE 调整下次复查日：事件留在待复查，{@code nextReviewDate} 必填</li>
 *   <li>PASS 复查合格申请放行：事件留在待复查等负责人确认；
 *       {@code nextReviewDate} 为空表示长期有效，有值则该合格结论过了那天即失效</li>
 * </ul>
 */
@Entity
@Table(name = "health_review")
public class HealthReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "horse_id", nullable = false)
    private Long horseId;

    @Column(name = "review_date", nullable = false)
    private LocalDate reviewDate;

    /** OBSERVE / RESCHEDULE / PASS */
    @Column(name = "result", nullable = false, length = 16)
    private String result;

    @Column(name = "conclusion", nullable = false, length = 500)
    private String conclusion;

    @Column(name = "next_review_date")
    private LocalDate nextReviewDate;

    /**
     * 复查依据的事件链版本（写入时该马 healthVersion 的值）。
     * 放行时最新事件的 registeredVersion 大于它 → 结论基于旧事件链，拒绝。
     */
    @Column(name = "based_version", nullable = false)
    private Long basedVersion = 0L;

    @Column(name = "reviewer", nullable = false, length = 64)
    private String reviewer;

    @Column(name = "role", nullable = false, length = 16)
    private String role;

    @Column(name = "request_id", unique = true, length = 64)
    private String requestId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getEventId() {
        return eventId;
    }

    public void setEventId(Long eventId) {
        this.eventId = eventId;
    }

    public Long getHorseId() {
        return horseId;
    }

    public void setHorseId(Long horseId) {
        this.horseId = horseId;
    }

    public LocalDate getReviewDate() {
        return reviewDate;
    }

    public void setReviewDate(LocalDate reviewDate) {
        this.reviewDate = reviewDate;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getConclusion() {
        return conclusion;
    }

    public void setConclusion(String conclusion) {
        this.conclusion = conclusion;
    }

    public LocalDate getNextReviewDate() {
        return nextReviewDate;
    }

    public void setNextReviewDate(LocalDate nextReviewDate) {
        this.nextReviewDate = nextReviewDate;
    }

    public Long getBasedVersion() {
        return basedVersion;
    }

    public void setBasedVersion(Long basedVersion) {
        this.basedVersion = basedVersion;
    }

    public String getReviewer() {
        return reviewer;
    }

    public void setReviewer(String reviewer) {
        this.reviewer = reviewer;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
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
}
