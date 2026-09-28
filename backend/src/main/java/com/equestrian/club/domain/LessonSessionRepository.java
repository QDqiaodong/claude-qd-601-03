package com.equestrian.club.domain;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LessonSessionRepository extends JpaRepository<LessonSession, Long> {

    List<LessonSession> findAllByOrderBySessionDateAscStartTimeAscIdAsc();

    /** 某一天某位教练的全部排期（同一教练同一时段不可重叠） */
    List<LessonSession> findByCoachIdAndSessionDate(Long coachId, LocalDate sessionDate);

    /** 训练日历：某匹马在某段日期内的排期 */
    List<LessonSession> findByHorseIdAndSessionDateBetweenOrderBySessionDateAscStartTimeAsc(
            Long horseId, LocalDate from, LocalDate to);

    /** 训练日历：某段日期内的全部排期 */
    List<LessonSession> findBySessionDateBetweenOrderBySessionDateAscStartTimeAsc(
            LocalDate from, LocalDate to);

    /**
     * 某匹马从某天起（含当天）的未取消排期：登记高风险健康事件时，
     * 这些场次要逐场标为受影响并挂到事件上（只标记，绝不删除）。
     */
    List<LessonSession> findByHorseIdAndSessionDateGreaterThanEqualAndStatusNotOrderBySessionDateAscStartTimeAsc(
            Long horseId, LocalDate from, String excludedStatus);

    long countByLessonId(Long lessonId);
}
