package com.equestrian.club.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HealthEventLogRepository extends JpaRepository<HealthEventLog, Long> {

    /** 事件链：时间正序，原始处置过程逐条回放 */
    List<HealthEventLog> findByEventIdOrderByIdAsc(Long eventId);

    List<HealthEventLog> findByHorseIdOrderByIdAsc(Long horseId);

    /** 幂等：同一 requestId 的重复提交直接取回第一次落的那条日志 */
    Optional<HealthEventLog> findByRequestId(String requestId);
}
