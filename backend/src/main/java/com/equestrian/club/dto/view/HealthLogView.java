package com.equestrian.club.dto.view;

import java.time.LocalDateTime;

/** 健康事件处置历史（只追加）视图 */
public record HealthLogView(
        Long id,
        Long eventId,
        String action,
        String actionName,
        String fromStatus,
        String fromStatusName,
        String toStatus,
        String toStatusName,
        String note,
        String operator,
        String role,
        String roleName,
        LocalDateTime operatedAt) {
}
