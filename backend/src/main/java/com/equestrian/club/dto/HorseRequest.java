package com.equestrian.club.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 马匹新增 / 修改请求体。
 * 所有字段都可为空（PUT 走局部更新，只覆盖非空字段），必填校验放在 service 里做。
 */
public class HorseRequest {

    private String horseNo;
    private String name;
    private String breed;
    private String gender;
    private Integer birthYear;
    private String status;
    private String rideLevel;

    /** 以下四个字段落在副表 horse_health */
    private LocalDate lastCheckDate;
    private Integer vaccineCount;
    private BigDecimal weightKg;
    private String healthNote;

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
}
