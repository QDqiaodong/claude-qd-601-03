package com.equestrian.club.domain;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

    List<Member> findAllByOrderByIdAsc();

    boolean existsByMemberNo(String memberNo);
}
