package com.equestrian.club.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface HorseRepository extends JpaRepository<Horse, Long> {

    List<Horse> findAllByOrderByIdAsc();

    Optional<Horse> findByHorseNo(String horseNo);

    boolean existsByHorseNo(String horseNo);

    /**
     * 行级悲观锁：健康事件链上的所有写操作（登记 / 流转 / 复查 / 放行）先锁马，
     * 把「版本比对 + 事件增改 + 马匹状态联动 + 版本号 +1」收成一条串行临界区，
     * 杜绝两个工作人员同时提交时都基于旧版本把状态写花。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from Horse h where h.id = :id")
    Optional<Horse> findByIdForUpdate(@Param("id") Long id);
}
