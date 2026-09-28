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
 * 会员。业务规则：会员卡余额与次数不足时不可预约；未通过初级考核不可约进阶课。
 * 表名用 club_member，避开 MEMBER 这个 MySQL 关键字。
 */
@Entity
@Table(name = "club_member")
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_no", nullable = false, unique = true, length = 32)
    private String memberNo;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    @Column(name = "phone", length = 32)
    private String phone;

    /** NORMAL 普通 / SILVER 银卡 / GOLD 金卡 / DIAMOND 钻石卡 */
    @Column(name = "level", nullable = false, length = 16)
    private String level;

    /** 会员卡余额 */
    @Column(name = "card_balance", nullable = false)
    private BigDecimal cardBalance;

    /** 会员卡剩余次数 */
    @Column(name = "card_times", nullable = false)
    private Integer cardTimes;

    /** 是否已通过初级考核 */
    @Column(name = "passed_basic", nullable = false)
    private Boolean passedBasic;

    /** NORMAL 正常 / FROZEN 冻结 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

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

    public String getMemberNo() {
        return memberNo;
    }

    public void setMemberNo(String memberNo) {
        this.memberNo = memberNo;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public BigDecimal getCardBalance() {
        return cardBalance;
    }

    public void setCardBalance(BigDecimal cardBalance) {
        this.cardBalance = cardBalance;
    }

    public Integer getCardTimes() {
        return cardTimes;
    }

    public void setCardTimes(Integer cardTimes) {
        this.cardTimes = cardTimes;
    }

    public Boolean getPassedBasic() {
        return passedBasic;
    }

    public void setPassedBasic(Boolean passedBasic) {
        this.passedBasic = passedBasic;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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
