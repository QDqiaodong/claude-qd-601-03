package com.equestrian.club.dto.view;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 交接事项视图。
 *
 * @param shiftNo       来源班次编号
 * @param targetShiftNo 承接班次编号（未分派为 null）
 * @param typeName      来源类型中文名（课程 / 马匹 / 栏位 / 手工）
 * @param changeKindName 课程变更细分中文名
 * @param statusName    状态中文名（待承接 / 已承接）
 * @param priorityName  优先级中文名
 * @param roleName      产生人角色中文名
 * @param incoming      是否「分派给当前查看班次」的来项（下一班首页用）
 * @param acceptance    承接记录（未承接为 null）
 * @param logs          操作流水（详情接口才返回，列表为 null）
 */
public record ItemView(
        Long id,
        String itemNo,
        Long shiftId,
        String shiftNo,
        Long targetShiftId,
        String targetShiftNo,
        String itemType,
        String typeName,
        String refType,
        Long refId,
        String changeKind,
        String changeKindName,
        String title,
        String detail,
        String priority,
        String priorityName,
        String ownerName,
        String status,
        String statusName,
        Long expectedVersion,
        LocalDateTime acceptedAt,
        String acceptedBy,
        String acceptedNote,
        String delayReason,
        LocalDateTime delayedAt,
        String delayedBy,
        String createdBy,
        String createdRole,
        String roleName,
        boolean incoming,
        AcceptanceView acceptance,
        List<ItemLogView> logs,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
