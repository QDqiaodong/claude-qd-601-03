package com.equestrian.club.domain;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CoachRepository extends JpaRepository<Coach, Long> {

    List<Coach> findAllByOrderByIdAsc();

    boolean existsByCoachNo(String coachNo);
}
