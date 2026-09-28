package com.equestrian.club.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * 健康事件与受影响排期的关联：哪次事件、在什么时间、把哪一场未来排期标为受影响。
 * 排期本身不删除（只把 health_affected 置 1），这里保留完整追溯链。
 */
@Entity
@Table(name = "health_event_session",
        uniqueConstraints = @UniqueConstraint(name = "uk_event_session", columnNames = {"event_id", "session_id"}))
public class HealthEventSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "horse_id", nullable = false)
    private Long horseId;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Column(name = "marked_at", nullable = false)
    private LocalDateTime markedAt;

    @Column(name = "marked_by", nullable = false, length = 64)
    private String markedBy;

    @PrePersist
    void onCreate() {
        if (markedAt == null) {
            markedAt = LocalDateTime.now();
        }
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

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public LocalDateTime getMarkedAt() {
        return markedAt;
    }

    public void setMarkedAt(LocalDateTime markedAt) {
        this.markedAt = markedAt;
    }

    public String getMarkedBy() {
        return markedBy;
    }

    public void setMarkedBy(String markedBy) {
        this.markedBy = markedBy;
    }
}
