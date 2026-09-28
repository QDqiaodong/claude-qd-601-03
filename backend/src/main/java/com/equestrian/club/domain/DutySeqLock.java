package com.equestrian.club.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 值班交接链全局序号锁：整库固定一行（id=1）。
 *
 * <p>开班 / 关班 / 交接期间课程变更入队先抢这把锁（SELECT ... FOR UPDATE），
 * 把「读取最新 OPEN 班次 + 事项生成 / 漏派校验 + 状态落库」收成一条串行临界区，
 * 不依赖多条 shift 行的加锁顺序，从根上避免「开班与关班同时发生」造成的循环等待
 * 以及「课程变更被挂到错误班次」。
 */
@Entity
@Table(name = "duty_seq_lock")
public class DutySeqLock {

    @Id
    @Column(name = "id")
    private Long id;

    /** 当前持锁说明（仅排障用） */
    @Column(name = "holder", length = 64)
    private String holder;

    @Column(name = "updated_at")
    private java.time.LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getHolder() {
        return holder;
    }

    public void setHolder(String holder) {
        this.holder = holder;
    }

    public java.time.LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(java.time.LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
