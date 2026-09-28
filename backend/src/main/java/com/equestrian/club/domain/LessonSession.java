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
 * 课程排期（课程的一次具体场次）。
 * 业务规则：同一教练同一天同一时段不可重叠；容量到顶后自动置为 FULL。
 */
@Entity
@Table(name = "lesson_session")
public class LessonSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lesson_id", nullable = false)
    private Long lessonId;

    @Column(name = "coach_id", nullable = false)
    private Long coachId;

    /** 可空外键：本场指定用马，未指定时为 null */
    @Column(name = "horse_id")
    private Long horseId;

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    /** 起始时间，形如 09:00 */
    @Column(name = "start_time", nullable = false, length = 5)
    private String startTime;

    /** 结束时间，形如 10:00 */
    @Column(name = "end_time", nullable = false, length = 5)
    private String endTime;

    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    @Column(name = "booked_count", nullable = false)
    private Integer bookedCount;

    /** SCHEDULED 已排期 / FULL 已满员 / CANCELED 已取消 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /**
     * 是否被健康事件标记为受影响：高风险事件把马打入休养时，其当时的未来未取消场次
     * 逐场置 1 并挂到 health_event_session 上。只标记不删除，追溯链保留。
     */
    @Column(name = "health_affected", nullable = false)
    private Boolean healthAffected = false;

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

    public Long getLessonId() {
        return lessonId;
    }

    public void setLessonId(Long lessonId) {
        this.lessonId = lessonId;
    }

    public Long getCoachId() {
        return coachId;
    }

    public void setCoachId(Long coachId) {
        this.coachId = coachId;
    }

    public Long getHorseId() {
        return horseId;
    }

    public void setHorseId(Long horseId) {
        this.horseId = horseId;
    }

    public LocalDate getSessionDate() {
        return sessionDate;
    }

    public void setSessionDate(LocalDate sessionDate) {
        this.sessionDate = sessionDate;
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

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public Integer getBookedCount() {
        return bookedCount;
    }

    public void setBookedCount(Integer bookedCount) {
        this.bookedCount = bookedCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getHealthAffected() {
        return healthAffected;
    }

    public void setHealthAffected(Boolean healthAffected) {
        this.healthAffected = healthAffected;
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
