package com.equestrian.club.dto.view;

import java.time.LocalDate;
import java.util.List;

/**
 * 马匹训练日历：行是固定时段、列是日期，格子里放该马当天该时段的排期与已约会员。
 * 前端「马匹卡片墙 + 训练日历」直接消费这个结构。
 */
public record HorseCalendarView(
        Long horseId,
        String horseNo,
        String horseName,
        String status,
        String statusName,
        String rideLevel,
        String rideLevelName,
        List<LocalDate> dates,
        List<String> slots,
        List<Cell> cells) {

    public record Cell(
            LocalDate date,
            String startTime,
            String endTime,
            Long sessionId,
            String lessonName,
            String category,
            String coachName,
            Integer capacity,
            Integer bookedCount,
            String status,
            String statusName,
            boolean healthAffected,
            List<CellRecord> records) {
    }

    public record CellRecord(
            Long recordId,
            Long memberId,
            String memberName,
            String status,
            String statusName) {
    }
}
