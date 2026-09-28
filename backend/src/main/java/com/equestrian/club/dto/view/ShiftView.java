package com.equestrian.club.dto.view;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 值班班次视图：班次头信息 + 交接清单。
 *
 * @param items           来源本班的事项（含快照 / 手工 / 期间变更），新事项在前
 * @param incomingItems   分派给本班、仍未承接的事项（跨班次来源，下一班首页核心）
 * @param pendingCount    来源本班未承接事项数
 * @param incomingPendingCount 分派给本班但仍未承接的事项数
 * @param nextShift       紧接其后的下一个班次（没有为 null；结班延期要派给它）
 * @param blockers        关班前置校验结果（空列表 = 可以直接关班）
 */
public record ShiftView(
        Long id,
        String shiftNo,
        LocalDate shiftDate,
        String leaderName,
        String handoverNote,
        String status,
        String statusName,
        Long itemVersion,
        LocalDateTime startedAt,
        LocalDateTime closedAt,
        List<ItemView> items,
        List<ItemView> incomingItems,
        long pendingCount,
        long incomingPendingCount,
        ShiftBriefView nextShift,
        List<CloseBlockerView> blockers,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
