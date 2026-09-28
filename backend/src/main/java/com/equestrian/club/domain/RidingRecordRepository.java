package com.equestrian.club.domain;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RidingRecordRepository extends JpaRepository<RidingRecord, Long> {

    List<RidingRecord> findAllByOrderByIdDesc();

    List<RidingRecord> findBySessionIdOrderByIdAsc(Long sessionId);

    List<RidingRecord> findByMemberIdOrderByRecordDateDescIdDesc(Long memberId);

    /** 训练日历：某匹马在某段日期内的骑乘记录 */
    List<RidingRecord> findByHorseIdAndRecordDateBetweenOrderByIdAsc(
            Long horseId, LocalDate from, LocalDate to);

    /** 同一会员、同一天、同一时段的有效记录（已取消的不算） */
    List<RidingRecord> findByMemberIdAndRecordDateAndStartTimeAndStatusNot(
            Long memberId, LocalDate recordDate, String startTime, String status);

    /** 某场排期的有效预约人数 */
    long countBySessionIdAndStatusNot(Long sessionId, String status);
}
