package com.equestrian.club.dto.view;

import java.math.BigDecimal;

/** 课程视图 */
public record LessonView(
        Long id,
        String lessonNo,
        String name,
        String category,
        String categoryName,
        Integer capacity,
        Integer durationMin,
        BigDecimal price,
        Integer timesCost,
        String status,
        String statusName) {
}
