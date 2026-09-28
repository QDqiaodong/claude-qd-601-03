package com.equestrian.club.dto.view;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 骑乘记录视图 */
public record RecordView(
        Long id,
        Long memberId,
        String memberNo,
        String memberName,
        Long sessionId,
        String lessonName,
        String category,
        String categoryName,
        String coachName,
        Long horseId,
        String horseNo,
        String horseName,
        LocalDate recordDate,
        String startTime,
        String endTime,
        String status,
        String statusName,
        BigDecimal fee,
        Integer timesUsed,
        String remark,
        LocalDateTime createdAt) {
}
