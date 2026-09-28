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
 * 骑乘课程（课程定义）：容量、类别、单次价格与消耗次数。
 * 类别分初级课 / 进阶课 / 私教课，进阶课与私教课都要求会员先通过初级考核。
 */
@Entity
@Table(name = "lesson")
public class Lesson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lesson_no", nullable = false, unique = true, length = 32)
    private String lessonNo;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    /** BASIC 初级课 / ADVANCED 进阶课 / PRIVATE 私教课 */
    @Column(name = "category", nullable = false, length = 16)
    private String category;

    /** 课程容量（单场最多几人） */
    @Column(name = "capacity", nullable = false)
    private Integer capacity;

    @Column(name = "duration_min", nullable = false)
    private Integer durationMin;

    @Column(name = "price", nullable = false)
    private BigDecimal price;

    /** 每次预约消耗的会员卡次数 */
    @Column(name = "times_cost", nullable = false)
    private Integer timesCost;

    /** ON 上架 / OFF 下架 */
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

    public String getLessonNo() {
        return lessonNo;
    }

    public void setLessonNo(String lessonNo) {
        this.lessonNo = lessonNo;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public Integer getDurationMin() {
        return durationMin;
    }

    public void setDurationMin(Integer durationMin) {
        this.durationMin = durationMin;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Integer getTimesCost() {
        return timesCost;
    }

    public void setTimesCost(Integer timesCost) {
        this.timesCost = timesCost;
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
