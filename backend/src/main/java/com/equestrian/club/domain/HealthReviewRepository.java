package com.equestrian.club.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HealthReviewRepository extends JpaRepository<HealthReview, Long> {

    /** 事件的复查历史，时间正序 */
    List<HealthReview> findByEventIdOrderByIdAsc(Long eventId);

    List<HealthReview> findByHorseIdOrderByIdAsc(Long horseId);

    /** 取某事件最后一条复查（放行判定依据的就是这条结论） */
    Optional<HealthReview> findFirstByEventIdOrderByIdDesc(Long eventId);

    /** 幂等：同一 requestId 的重复复查只落一条 */
    Optional<HealthReview> findByRequestId(String requestId);
}
