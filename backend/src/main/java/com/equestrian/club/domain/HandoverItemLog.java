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
 * 交接事项操作流水：只追加，永不修改、永不删除。
 *
 * <p>CREATE 建项 / SUPPLEMENT 补充 / PRIORITY 调整优先级 / ASSIGN 分派负责人 /
 * DISPATCH 分派班次 / ACCEPT 承接 / DELAY 记录延期 各落一条。
 * {@code requestId} 唯一约束兜底：重复提交共用一条流水。
 */
@Entity
@Table(name = "handover_item_log")
public class HandoverItemLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    /** CREATE / SUPPLEMENT / PRIORITY / ASSIGN / DISPATCH / ACCEPT / DELAY */
    @Column(name = "action", nullable = false, length = 24)
    private String action;

    @Column(name = "note", nullable = false, length = 500)
    private String note;

    @Column(name = "operator", nullable = false, length = 64)
    private String operator;

    /** STAFF 普通教练 / MANAGER 值班负责人 */
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

    public Long getItemId() {
        return itemId;
    }

    public void setItemId(Long itemId) {
        this.itemId = itemId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
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
