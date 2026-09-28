package com.equestrian.club.dto.view;

/**
 * 排期上的交接摘要：课程详情要显示「它属于哪个班次」以及「最近一次承接记录」。
 * 列表接口只给摘要；完整事项（操作流水等）在值班交接台详情里看。
 *
 * @param shiftNo         产生该交接事项的来源班次编号
 * @param shiftDate       来源班次日期
 * @param targetShiftNo   当前承接班次编号（未分派为 null）
 * @param itemNo          事项编号
 * @param itemStatus      事项状态 PENDING / ACCEPTED
 * @param itemStatusName  事项状态中文名
 * @param changeKindName  课程变更中文名（新排期 / 取消排期 / 健康受影响 / 已满员）
 * @param acceptedBy      最近一次承接人（未承接为 null）
 * @param acceptedShiftNo 最近一次承接发生在哪个班次（未承接为 null）
 */
public record SessionHandoverView(
        String shiftNo,
        java.time.LocalDate shiftDate,
        String targetShiftNo,
        String itemNo,
        String itemStatus,
        String itemStatusName,
        String changeKindName,
        String acceptedBy,
        String acceptedShiftNo) {
}
