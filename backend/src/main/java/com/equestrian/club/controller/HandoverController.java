package com.equestrian.club.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.equestrian.club.common.ApiResponse;
import com.equestrian.club.dto.ItemAcceptRequest;
import com.equestrian.club.dto.ItemCreateRequest;
import com.equestrian.club.dto.ItemDispatchRequest;
import com.equestrian.club.dto.ItemSupplementRequest;
import com.equestrian.club.dto.ItemUpdateRequest;
import com.equestrian.club.dto.ShiftCloseRequest;
import com.equestrian.club.dto.ShiftCreateRequest;
import com.equestrian.club.dto.ShiftNoteRequest;
import com.equestrian.club.dto.view.CloseBlockerView;
import com.equestrian.club.dto.view.ItemView;
import com.equestrian.club.dto.view.ShiftBriefView;
import com.equestrian.club.dto.view.ShiftView;
import com.equestrian.club.service.HandoverService;

/**
 * 值班交接台：
 * 开班（自动生成交接清单）/ 当前班首页 / 历史查询 / 事项补充 / 优先级 / 分派 /
 * 下一班逐项承接 / 结班逐项延期。
 *
 * <p>角色与归属校验全部在后端：开班 / 结班 / 分派 / 承接只允许 MANAGER，
 * 且必须是对应班次负责人本人；普通教练（STAFF）只能补充自己负责的事项。
 */
@RestController
@RequestMapping("/api/duty")
public class HandoverController {

    private final HandoverService handoverService;

    public HandoverController(HandoverService handoverService) {
        this.handoverService = handoverService;
    }

    /** 首页：最新一个进行中班次（含未承接来项）；无进行中班次时 data 为 null */
    @GetMapping("/shifts/active")
    public ApiResponse<ShiftView> active() {
        return ApiResponse.success(handoverService.activeBoard());
    }

    /** 历史班次：按日期（date 单日，或 from/to 区间）、负责人、事项状态查询 */
    @GetMapping("/shifts")
    public ApiResponse<List<ShiftBriefView>> history(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String leaderName,
            @RequestParam(required = false) String itemStatus) {
        LocalDate rangeFrom = date != null ? date : from;
        LocalDate rangeTo = date != null ? date : to;
        return ApiResponse.success(handoverService.history(rangeFrom, rangeTo, leaderName, itemStatus));
    }

    @GetMapping("/shifts/{id}")
    public ApiResponse<ShiftView> detail(@PathVariable Long id) {
        return ApiResponse.success(handoverService.detail(id));
    }

    /** 值班负责人开班：系统从当班课程 / 马匹状态 / 栏位安排生成交接清单 */
    @PostMapping("/shifts")
    public ApiResponse<ShiftView> createShift(@RequestBody ShiftCreateRequest request) {
        return ApiResponse.success("值班班次已建立，交接清单已从当班课程、马匹状态与栏位安排生成",
                handoverService.createShift(request));
    }

    /** 补充当班交接总说明（本班负责人） */
    @PutMapping("/shifts/{id}/note")
    public ApiResponse<ShiftView> updateNote(@PathVariable Long id,
            @RequestBody ShiftNoteRequest request) {
        return ApiResponse.success("交接说明已更新", handoverService.updateNote(id, request));
    }

    /** 关班预检：逐项列出仍未承接的事项（前端据此逐项要求延期原因，阻止误关班） */
    @GetMapping("/shifts/{id}/close-blockers")
    public ApiResponse<List<CloseBlockerView>> closeBlockers(@PathVariable Long id) {
        return ApiResponse.success(handoverService.closeBlockers(id));
    }    /** 结束班次：有未承接事项时必须逐项记录延期原因，整体原子完成或整体拒绝 */
    @PostMapping("/shifts/{id}/close")
    public ApiResponse<ShiftView> close(@PathVariable Long id,
            @RequestBody ShiftCloseRequest request) {
        return ApiResponse.success("班次已结束，未承接事项已随交接转入下一班并保留延期原因",
                handoverService.close(id, request));
    }

    /** 值班负责人手工新增交接事项 */
    @PostMapping("/shifts/{id}/items")
    public ApiResponse<ItemView> createItem(@PathVariable Long id,
            @RequestBody ItemCreateRequest request) {
        return ApiResponse.success("交接事项已新增", handoverService.createItem(id, request));
    }

    /** 事项详情（完整操作流水） */
    @GetMapping("/items/{id}")
    public ApiResponse<ItemView> itemDetail(@PathVariable Long id) {
        return ApiResponse.success(handoverService.detailItem(id));
    }

    /** 补充事项说明（普通教练只能补充自己负责的事项） */
    @PostMapping("/items/{id}/supplement")
    public ApiResponse<ItemView> supplement(@PathVariable Long id,
            @RequestBody ItemSupplementRequest request) {
        return ApiResponse.success("说明已补充（既有记录未被覆盖）",
                handoverService.supplement(id, request));
    }

    /** 值班负责人调整优先级 / 分派负责教练 */
    @PostMapping("/items/{id}")
    public ApiResponse<ItemView> updateItem(@PathVariable Long id,
            @RequestBody ItemUpdateRequest request) {
        return ApiResponse.success("事项已更新", handoverService.updateItem(id, request));
    }

    /** 值班负责人把事项分派给下一班 */
    @PostMapping("/items/{id}/dispatch")
    public ApiResponse<ItemView> dispatch(@PathVariable Long id,
            @RequestBody ItemDispatchRequest request) {
        return ApiResponse.success("事项已分派给下一班", handoverService.dispatch(id, request));
    }

    /** 下一班负责人逐项确认承接（重复确认不产生重复记录） */
    @PostMapping("/items/{id}/accept")
    public ApiResponse<ItemView> accept(@PathVariable Long id,
            @RequestBody ItemAcceptRequest request) {
        return ApiResponse.success("事项已逐项确认承接", handoverService.accept(id, request));
    }
}
