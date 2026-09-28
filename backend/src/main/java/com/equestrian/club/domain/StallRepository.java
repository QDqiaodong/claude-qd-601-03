package com.equestrian.club.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StallRepository extends JpaRepository<Stall, Long> {

    List<Stall> findAllByOrderByIdAsc();

    Optional<Stall> findByStallNo(String stallNo);

    boolean existsByStallNo(String stallNo);

    /** 找出某匹马当前占用的栏位（一匹马只能占一个栏位） */
    Optional<Stall> findByHorseId(Long horseId);
}
