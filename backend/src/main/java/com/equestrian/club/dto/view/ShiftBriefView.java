package com.equestrian.club.dto.view;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 班次简要视图（列表 / 下一班指针用）。
 */
public record ShiftBriefView(
        Long id,
        String shiftNo,
        LocalDate shiftDate,
        String leaderName,
        String status,
        String statusName,
        long pendingCount,
        long incomingPendingCount,
        LocalDateTime startedAt,
        LocalDateTime closedAt) {
}
