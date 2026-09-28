package com.equestrian.club.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HandoverItemLogRepository extends JpaRepository<HandoverItemLog, Long> {

    /** 事项操作流水：时间正序回放 */
    List<HandoverItemLog> findByItemIdOrderByIdAsc(Long itemId);

    /** 幂等：同一 requestId 的重复提交直接取回第一次落的那条流水 */
    Optional<HandoverItemLog> findByRequestId(String requestId);
}
