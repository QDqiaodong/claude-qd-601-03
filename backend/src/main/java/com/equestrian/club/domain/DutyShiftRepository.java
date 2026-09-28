package com.equestrian.club.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DutyShiftRepository extends JpaRepository<DutyShift, Long> {

    /** 最新班次（编号取号 / 判断当前班用） */
    Optional<DutyShift> findFirstByOrderByIdDesc();

    /** 当前最新一个进行中班次（课程变更实时挂入它的交接清单） */
    Optional<DutyShift> findFirstByStatusOrderByIdDesc(String status);

    /** 首页 / 历史查询：按日期范围 */
    List<DutyShift> findByShiftDateBetweenOrderByIdDesc(LocalDate from, LocalDate to);

    List<DutyShift> findByShiftDateOrderByIdDesc(LocalDate date);

    List<DutyShift> findAllByOrderByIdDesc();

    /** 历史按负责人查询（同名负责人带日期范围） */
    List<DutyShift> findByShiftDateBetweenAndLeaderNameContainingOrderByIdDesc(
            LocalDate from, LocalDate to, String leaderName);

    List<DutyShift> findByLeaderNameContainingOrderByIdDesc(String leaderName);

    long countByStatus(String status);

    /** 开班幂等：同一 requestId 重复提交只取回同一个班次 */
    Optional<DutyShift> findByCreateRequestId(String createRequestId);
}
