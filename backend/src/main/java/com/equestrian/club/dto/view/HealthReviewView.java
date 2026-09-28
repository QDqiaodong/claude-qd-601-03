package com.equestrian.club.dto.view;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 健康事件复查记录视图 */
public record HealthReviewView(
        Long id,
        Long eventId,
        LocalDate reviewDate,
        String result,
        String resultName,
        String conclusion,
        LocalDate nextReviewDate,
        String reviewer,
        String role,
        String roleName,
        boolean expired,
        LocalDateTime createdAt) {
}
