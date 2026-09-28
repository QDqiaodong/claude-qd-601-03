package com.equestrian.club.dto.view;

import java.util.List;

/**
 * 马匹健康总览（马匹详情页右侧一屏看清当前风险）：
 * 未闭环事件清单 + 最近一次复查结论 + 是否具备复训放行条件（及不满足的具体原因）。
 */
public record HorseHealthView(
        Long horseId,
        String horseNo,
        String horseName,
        String horseStatus,
        String horseStatusName,
        Long healthVersion,
        /** 仍未闭环的事件（新事件在前） */
        List<HealthEventView> openEvents,
        /** 全马最近一次复查结论（跨事件取最新一条） */
        HealthReviewView latestReview,
        /** 当前是否允许负责人确认复训放行 */
        boolean releasable,
        /** 不能放行的具体原因（releasable=false 时逐条给出） */
        List<String> releaseBlockers,
        /** 未闭环事件数 */
        int openCount,
        /** 其中已逾期数 */
        int overdueCount) {
}
