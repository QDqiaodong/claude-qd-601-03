package com.equestrian.club.domain;

import java.math.BigDecimal;
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
 * 马房栏位。业务规则：栏位编号唯一 / 一个栏位只能放一匹马 / 一匹马只能占一个栏位 / 维护中禁止入栏。
 */
@Entity
@Table(name = "stall")
public class Stall {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stall_no", nullable = false, unique = true, length = 32)
    private String stallNo;

    @Column(name = "barn_name", nullable = false, length = 64)
    private String barnName;

    @Column(name = "area_sqm")
    private BigDecimal areaSqm;

    /** IDLE 空闲 / OCCUPIED 占用 / MAINTENANCE 维护中 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** 可空外键：占用该栏位的马匹，未占用时为 null */
    @Column(name = "horse_id")
    private Long horseId;

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

    public String getStallNo() {
        return stallNo;
    }

    public void setStallNo(String stallNo) {
        this.stallNo = stallNo;
    }

    public String getBarnName() {
        return barnName;
    }

    public void setBarnName(String barnName) {
        this.barnName = barnName;
    }

    public BigDecimal getAreaSqm() {
        return areaSqm;
    }

    public void setAreaSqm(BigDecimal areaSqm) {
        this.areaSqm = areaSqm;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getHorseId() {
        return horseId;
    }

    public void setHorseId(Long horseId) {
        this.horseId = horseId;
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
