package com.equestrian.club.dto.view;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 被健康事件标记为受影响的排期（事件详情里的追溯条目） */
public record AffectedSessionView(
        Long sessionId,
        LocalDate sessionDate,
        String startTime,
        String endTime,
        String lessonName,
        String coachName,
        String status,
        String statusName,
        Integer bookedCount,
        LocalDateTime markedAt,
        String markedBy) {
}
