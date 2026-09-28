package com.equestrian.club.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.SecondaryTable;
import jakarta.persistence.Table;

/**
 * 马匹档案（本仓技术路线差异点）。
 *
 * <p>同一个实体映射两张表：
 * <ul>
 *   <li>主表 {@code horse} —— 编号、姓名、品种、状态等在役信息</li>
 *   <li>副表 {@code horse_health} —— 体检日期、疫苗次数、体重、健康备注</li>
 * </ul>
 * 副表字段通过 {@code @Column(table = "horse_health")} 指定，读写时由 Hibernate 自动
 * 在主表与副表之间拆装 SQL，接口层看到的是一个完整的马匹档案。
 *
 * <p>注意：业务字段一律不在实体上写默认值，默认值统一放在 HorseService.create 里，
 * 否则 PUT 局部更新时会用实体默认值把用户已有的数据刷回去。
 * 只有 createdAt / updatedAt 这类审计字段允许在生命周期回调里赋值。
 */
@Entity
@Table(name = "horse")
@SecondaryTable(name = "horse_health", pkJoinColumns = @PrimaryKeyJoinColumn(name = "horse_id"))
public class Horse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 马匹编号，全局唯一 */
    @Column(name = "horse_no", nullable = false, unique = true, length = 32)
    private String horseNo;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    @Column(name = "breed", length = 32)
    private String breed;

    /** MALE 公 / FEMALE 母 / GELDING 骟 */
    @Column(name = "gender", length = 16)
    private String gender;

    @Column(name = "birth_year")
    private Integer birthYear;

    /** 在役状态机：ACTIVE 在役 / RESTING 休养 / RETIRED 退役 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** 骑乘等级：BEGINNER_SAFE / INTERMEDIATE / ADVANCED */
    @Column(name = "ride_level", nullable = false, length = 16)
    private String rideLevel;

    /**
     * 健康事件链版本（手工维护的乐观锁，不用 JPA @Version 以免影响既有 PUT 局部更新）。
     * 每登记 / 流转 / 补充 / 复查 / 放行一次 +1，前端提交时回传所见版本，落后即 409。
     */
    @Column(name = "health_version", nullable = false)
    private Long healthVersion = 0L;

    // ---------------- 以下字段落在副表 horse_health ----------------

    @Column(table = "horse_health", name = "last_check_date")
    private LocalDate lastCheckDate;

    @Column(table = "horse_health", name = "vaccine_count")
    private Integer vaccineCount;

    @Column(table = "horse_health", name = "weight_kg")
    private BigDecimal weightKg;

    @Column(table = "horse_health", name = "health_note", length = 255)
    private String healthNote;

    // ---------------- 审计字段 ----------------

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

    public String getHorseNo() {
        return horseNo;
    }

    public void setHorseNo(String horseNo) {
        this.horseNo = horseNo;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBreed() {
        return breed;
    }

    public void setBreed(String breed) {
        this.breed = breed;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public Integer getBirthYear() {
        return birthYear;
    }

    public void setBirthYear(Integer birthYear) {
        this.birthYear = birthYear;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRideLevel() {
        return rideLevel;
    }

    public void setRideLevel(String rideLevel) {
        this.rideLevel = rideLevel;
    }

    public Long getHealthVersion() {
        return healthVersion;
    }

    public void setHealthVersion(Long healthVersion) {
        this.healthVersion = healthVersion;
    }

    public LocalDate getLastCheckDate() {
        return lastCheckDate;
    }

    public void setLastCheckDate(LocalDate lastCheckDate) {
        this.lastCheckDate = lastCheckDate;
    }

    public Integer getVaccineCount() {
        return vaccineCount;
    }

    public void setVaccineCount(Integer vaccineCount) {
        this.vaccineCount = vaccineCount;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }

    public String getHealthNote() {
        return healthNote;
    }

    public void setHealthNote(String healthNote) {
        this.healthNote = healthNote;
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
