package com.equestrian.club.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HandoverAcceptanceRepository extends JpaRepository<HandoverAcceptance, Long> {

    Optional<HandoverAcceptance> findByItemId(Long itemId);

    /** 幂等：同一 requestId 重复承接只取回同一条 */
    Optional<HandoverAcceptance> findByRequestId(String requestId);

    List<HandoverAcceptance> findByShiftIdOrderByIdAsc(Long shiftId);
}
