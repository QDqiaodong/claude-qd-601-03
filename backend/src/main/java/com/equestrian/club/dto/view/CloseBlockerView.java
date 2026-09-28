package com.equestrian.club.dto.view;

/**
 * 关班逐项拦截项：结束班次前仍未承接的每个事项一条，前端据此逐项要求填延期原因。
 *
 * @param itemId       事项 id
 * @param itemNo       事项编号
 * @param title        事项标题
 * @param dispatched   本班产生且已分派给下一班（true=等承接；false=还没分派，结班时自动转交）
 * @param incoming     true=更早的班次转入本班、本班仍未承接的来项（结班时继续顺延给下一班）
 * @param targetShiftNo 已分派时的承接班次编号
 * @param reason       拦截原因（逐项提示）
 */
public record CloseBlockerView(
        Long itemId,
        String itemNo,
        String title,
        boolean dispatched,
        boolean incoming,
        String targetShiftNo,
        String reason) {
}
