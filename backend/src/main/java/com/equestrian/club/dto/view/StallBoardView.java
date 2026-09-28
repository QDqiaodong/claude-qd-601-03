package com.equestrian.club.dto.view;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 栏位看板的单轮读取结果（快照）。
 *
 * 顶部汇总 {@link #summary()}、马房分组 {@link #barns()} 与栏位明细 {@link #stalls()}
 * 在同一个只读事务里一次性算出，三者来自同一批 stall 行：
 * 前端看板任何一个数字都能从 stalls 反推回来，不会出现明细已变、汇总仍旧值的情况。
 *
 * generatedAt 是这轮结果的生成时间，页面据此展示「最后更新于 …」，
 * 证明当前展示的统计、分组、卡片同属一轮加载。
 */
public record StallBoardView(
        LocalDateTime generatedAt,
        Summary summary,
        List<Barn> barns,
        List<StallView> stalls) {

    /** 顶部汇总：total = idle + occupied + maintenance + unknown，重复刷新不累加 */
    public record Summary(
            long total,
            long idle,
            long occupied,
            long maintenance,
            long unknown) {
    }

    /**
     * 马房分组：total / occupied 与该组 stalls 严格一致（sum 起来等于顶部汇总）。
     * barnName 为空 / 全空白的栏位归入固定的「未分组马房」，保证每个栏位都能被分组展示。
     */
    public record Barn(
            String barnName,
            long total,
            long occupied,
            List<StallView> stalls) {
    }
}
