package com.equestrian.club.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface HealthEventRepository extends JpaRepository<HealthEvent, Long> {

    List<HealthEvent> findAllByOrderByIdDesc();

    /** 处置台列表：按马筛选，新事件在前 */
    List<HealthEvent> findByHorseIdOrderByIdDesc(Long horseId);

    /** 按状态筛选（含「是否未关闭」由 service 组装） */
    List<HealthEvent> findByStatusOrderByIdDesc(String status);

    List<HealthEvent> findByHorseIdAndStatusOrderByIdDesc(Long horseId, String status);

    /** 某匹马全部未关闭事件（复训放行判定用），按登记先后返回 */
    List<HealthEvent> findByHorseIdAndStatusNotOrderByIdAsc(Long horseId, String status);

    boolean existsByHorseIdAndStatusNot(Long horseId, String status);

    /** 事件编号取号：当前最大数字部分 */
    Optional<HealthEvent> findFirstByOrderByIdDesc();
}
