package com.equestrian.club.dto.view;

/** 教练视图 */
public record CoachView(
        Long id,
        String coachNo,
        String name,
        String level,
        String levelName,
        String phone,
        String status,
        String statusName) {
}
