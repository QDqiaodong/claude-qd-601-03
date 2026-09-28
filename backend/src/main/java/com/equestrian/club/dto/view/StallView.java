package com.equestrian.club.dto.view;

import java.math.BigDecimal;

/**
 * 栏位视图：附带当前占用马匹的编号与姓名。
 *
 * horseMissing 表示「栏位处于占用状态、horse_id 非空，但马匹档案查不到」的历史脏数据：
 * 看板不能因为一条坏引用就整个加载失败，也不能把它静默当成空栏，
 * 卡片要明确提示「占用马匹档案缺失」，并且仍然计入占用。
 */
public record StallView(
        Long id,
        String stallNo,
        String barnName,
        BigDecimal areaSqm,
        String status,
        String statusName,
        Long horseId,
        String horseNo,
        String horseName,
        boolean horseMissing) {
}
