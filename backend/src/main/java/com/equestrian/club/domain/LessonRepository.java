package com.equestrian.club.domain;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LessonRepository extends JpaRepository<Lesson, Long> {

    List<Lesson> findAllByOrderByIdAsc();

    boolean existsByLessonNo(String lessonNo);
}
