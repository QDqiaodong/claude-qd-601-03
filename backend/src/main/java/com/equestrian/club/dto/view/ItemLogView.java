package com.equestrian.club.dto.view;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 交接事项操作流水视图（只追加链的回放）。
 */
public record ItemLogView(
        Long id,
        Long itemId,
        String action,
        String actionName,
        String note,
        String operator,
        String role,
        String roleName,
        LocalDateTime createdAt) {
}
