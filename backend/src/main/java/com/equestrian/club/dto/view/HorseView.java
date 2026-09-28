package com.equestrian.club.dto.view;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 马匹档案视图：基础信息（horse 主表）+ 健康档案（horse_health 副表）拼成一条返回。
 * healthVersion 是健康事件链版本号，打开处置表单时回传，用于并发冲突检测。
 */
public record HorseView(
        Long id,
        String horseNo,
        String name,
        String breed,
        String gender,
        Integer birthYear,
        Integer age,
        String status,
        String statusName,
        String rideLevel,
        String rideLevelName,
        LocalDate lastCheckDate,
        Integer vaccineCount,
        BigDecimal weightKg,
        String healthNote,
        Long healthVersion,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
