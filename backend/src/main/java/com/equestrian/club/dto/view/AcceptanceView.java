package com.equestrian.club.dto.view;

import java.time.LocalDateTime;

/**
 * 承接确认视图（课程详情的「最近一次承接记录」也读它）。
 */
public record AcceptanceView(
        Long id,
        Long itemId,
        Long shiftId,
        String shiftNo,
        String acceptor,
        String note,
        LocalDateTime createdAt) {
}
