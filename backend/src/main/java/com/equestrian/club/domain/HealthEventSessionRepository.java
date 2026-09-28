package com.equestrian.club.domain;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HealthEventSessionRepository extends JpaRepository<HealthEventSession, Long> {

    /** 事件详情里追溯「这次伤病影响了哪些场次」 */
    List<HealthEventSession> findByEventIdOrderByIdAsc(Long eventId);

    List<HealthEventSession> findByHorseIdOrderByIdDesc(Long horseId);

    boolean existsByEventIdAndSessionId(Long eventId, Long sessionId);
}
