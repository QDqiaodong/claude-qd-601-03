package com.equestrian.club.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface HandoverItemRepository extends JpaRepository<HandoverItem, Long> {

    /** 事项详情 / 编辑：对事项行上悲观锁，把「版本比对 + 状态修改」收成临界区 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from HandoverItem i where i.id = :id")
    Optional<HandoverItem> findByIdForUpdate(@Param("id") Long id);

    /** 来源班次的全部事项（详情 / 关班漏项校验），新事项在前 */
    List<HandoverItem> findByShiftIdOrderByIdDesc(Long shiftId);

    /** 承接班次首页：分派给本班的事项 */
    List<HandoverItem> findByTargetShiftIdOrderByIdAsc(Long targetShiftId);

    /** 下一班首页核心：分派给本班但仍未承接（未确认不能消失） */
    List<HandoverItem> findByTargetShiftIdAndStatusOrderByIdAsc(Long targetShiftId, String status);

    /** 某班次关于某排期的全部交接事项（含各次变更） */
    List<HandoverItem> findByShiftIdAndRefTypeAndRefId(Long shiftId, String refType, Long refId);

    /** 全部班次关于某排期的交接事项（课程详情问「它属于哪个班次」） */
    List<HandoverItem> findByRefTypeAndRefId(String refType, Long refId);

    Optional<HandoverItem> findFirstByOrderByIdDesc();

    /**
     * 未承接事项业务去重：同一班次对同一业务对象（ref_type + ref_id + change_kind）
     * 已有未承接事项时，开班快照与实时课程变更都不再重复建项。
     */
    Optional<HandoverItem> findByShiftIdAndRefTypeAndRefIdAndChangeKindAndStatus(
            Long shiftId, String refType, Long refId, String changeKind, String status);

    long countByShiftIdAndStatus(Long shiftId, String status);

    /** 历史班次按事项状态筛选：事项属于该班（来源或承接）即计入 */
    @Query("select distinct i.shiftId from HandoverItem i where i.status = :status and i.shiftId in :shiftIds")
    List<Long> shiftIdsProducedWithStatus(@Param("shiftIds") List<Long> shiftIds, @Param("status") String status);

    @Query("select distinct i.targetShiftId from HandoverItem i where i.status = :status and i.targetShiftId in :shiftIds")
    List<Long> shiftIdsIncomingWithStatus(@Param("shiftIds") List<Long> shiftIds, @Param("status") String status);
}
