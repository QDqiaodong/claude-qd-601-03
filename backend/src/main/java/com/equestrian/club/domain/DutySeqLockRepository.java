package com.equestrian.club.domain;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface DutySeqLockRepository extends JpaRepository<DutySeqLock, Long> {

    /**
     * 全局序号锁（固定 id=1）：开班 / 关班 / 课程变更入队先抢它，
     * 串行化「找最新 OPEN 班次 + 事项挂入 / 漏派校验 + 落库」整条链。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from DutySeqLock l where l.id = 1")
    Optional<DutySeqLock> lock();
}
