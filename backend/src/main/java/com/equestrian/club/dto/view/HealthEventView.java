package com.equestrian.club.dto.view;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 健康事件视图。
 * 列表接口给概要字段（reviews / logs / affectedSessions 为空）；
 * 详情接口给完整事件链：基本信息 + 复查历史 + 处置历史（只追加）+ 受影响排期。
 */
public record HealthEventView(
        Long id,
        String eventNo,
        Long horseId,
        String horseNo,
        String horseName,
        String horseStatus,
        String horseStatusName,
        LocalDateTime occurredAt,
        String severity,
        String severityName,
        boolean highRisk,
        String symptom,
        String treatmentAdvice,
        LocalDate expectedReviewDate,
        String status,
        String statusName,
        String createdBy,
        String createdRole,
        LocalDateTime closedAt,
        String closedBy,
        /** 预计复查日已过且事件未关闭（列表「是否逾期」筛选依据） */
        boolean overdue,
        /** 最近一次复查结论（马匹详情也直接展示这条） */
        HealthReviewView latestReview,
        Long horseHealthVersion,
        List<HealthReviewView> reviews,
        List<HealthLogView> logs,
        List<AffectedSessionView> affectedSessions,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
