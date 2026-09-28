package com.equestrian.club.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * 健康事件处置历史：只追加，不提供 update / delete。
 *
 * <p>登记、补充处置、状态流转、复查、复训放行各落一条；历史发生过的处置说明
 * 不能被后续编辑覆盖。{@code requestId} 是前端每次点击生成的幂等键，
 * 重复点击（双击、网络重试）只会取回同一条日志。
 */
@Entity
@Table(name = "health_event_log")
public class HealthEventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "horse_id", nullable = false)
    private Long horseId;

    /** REGISTER / SUPPLEMENT / TRANSITION / REVIEW / RELEASE */
    @Column(name = "action", nullable = false, length = 24)
    private String action;

    @Column(name = "from_status", length = 20)
    private String fromStatus;

    @Column(name = "to_status", length = 20)
    private String toStatus;

    @Column(name = "note", nullable = false, length = 500)
    private String note;

    @Column(name = "operator", nullable = false, length = 64)
    private String operator;

    /** STAFF / MANAGER */
    @Column(name = "role", nullable = false, length = 16)
    private String role;

    /** 前端幂等键；库里同值只允许存在一条 */
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

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public void setFromStatus(String fromStatus) {
        this.fromStatus = fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public void setToStatus(String toStatus) {
        this.toStatus = toStatus;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
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
