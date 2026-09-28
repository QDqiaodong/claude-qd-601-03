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
 * 交接事项承接确认：逐项承接，一个事项至多一条（uk_acceptance_item 兜底）。
 *
 * <p>{@code requestId} 是前端为一次「承接」点击生成的幂等键：重复点击 / 网络重试
 * 只会取回同一条承接记录，绝不产生重复承接。
 */
@Entity
@Table(name = "handover_acceptance")
public class HandoverAcceptance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_id", nullable = false, unique = true)
    private Long itemId;

    /** 承接班次（下一班） */
    @Column(name = "shift_id", nullable = false)
    private Long shiftId;

    /** 承接负责人 */
    @Column(name = "acceptor", nullable = false, length = 64)
    private String acceptor;

    @Column(name = "note", length = 500)
    private String note;

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

    public Long getShiftId() {
        return shiftId;
    }

    public void setShiftId(Long shiftId) {
        this.shiftId = shiftId;
    }

    public String getAcceptor() {
        return acceptor;
    }

    public void setAcceptor(String acceptor) {
        this.acceptor = acceptor;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
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
