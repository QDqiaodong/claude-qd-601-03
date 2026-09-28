package com.equestrian.club.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.equestrian.club.common.ApiResponse;
import com.equestrian.club.dto.HealthEventCreateRequest;
import com.equestrian.club.dto.HealthReleaseRequest;
import com.equestrian.club.dto.HealthReviewRequest;
import com.equestrian.club.dto.HealthSupplementRequest;
import com.equestrian.club.dto.HealthTransitionRequest;
import com.equestrian.club.dto.view.HealthEventView;
import com.equestrian.club.dto.view.HorseHealthView;
import com.equestrian.club.service.HealthEventService;

/**
 * 马匹健康事件处置台：
 * 列表筛选 / 事件链详情 / 马匹健康总览 / 登记 / 补充 / 流转 / 复查 / 负责人复训放行。
 *
 * <p>所有写接口的角色校验（放行只能 MANAGER）、并发版本校验、幂等去重都在后端完成，
 * 前端隐藏按钮只做体验，不承担业务校验。
 */
@RestController
@RequestMapping("/api/health-events")
public class HealthEventController {

    private final HealthEventService healthEventService;

    public HealthEventController(HealthEventService healthEventService) {
        this.healthEventService = healthEventService;
    }

    /** 处置台列表：horseId 按马、status 按状态、overdue=true 只看逾期（可组合） */
    @GetMapping
    public ApiResponse<List<HealthEventView>> list(
            @RequestParam(required = false) Long horseId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Boolean overdue) {
        return ApiResponse.success(healthEventService.list(horseId, status, overdue));
    }

    @GetMapping("/{id}")
    public ApiResponse<HealthEventView> detail(@PathVariable Long id) {
        return ApiResponse.success(healthEventService.detail(id));
    }

    /** 马匹详情：未闭环事件 + 最近复查结论 + 放行条件预检（不满足时逐条给原因） */
    @GetMapping("/horse/{horseId}/overview")
    public ApiResponse<HorseHealthView> overview(@PathVariable Long horseId) {
        return ApiResponse.success(healthEventService.horseHealth(horseId));
    }

    @PostMapping
    public ApiResponse<HealthEventView> create(@RequestBody HealthEventCreateRequest request) {
        HealthEventView view = healthEventService.create(request);
        String message = view.highRisk()
                ? "高风险事件已登记：马匹已立即转入休养，名下未来排期已标记受影响（未删除），可在事件中追溯"
                : "健康事件已登记";
        return ApiResponse.success(message, view);
    }

    @PostMapping("/{id}/supplement")
    public ApiResponse<HealthEventView> supplement(@PathVariable Long id,
            @RequestBody HealthSupplementRequest request) {
        return ApiResponse.success("处置说明已补充（历史记录未被覆盖）", healthEventService.supplement(id, request));
    }

    @PostMapping("/{id}/transition")
    public ApiResponse<HealthEventView> transition(@PathVariable Long id,
            @RequestBody HealthTransitionRequest request) {
        return ApiResponse.success("事件状态已流转", healthEventService.transition(id, request));
    }

    @PostMapping("/{id}/reviews")
    public ApiResponse<HealthEventView> review(@PathVariable Long id,
            @RequestBody HealthReviewRequest request) {
        return ApiResponse.success("复查记录已追加", healthEventService.review(id, request));
    }

    /** 负责人复训放行（后端强制 MANAGER，普通工作人员直调接口也会被拒绝） */
    @PostMapping("/horse/{horseId}/release")
    public ApiResponse<HorseHealthView> release(@PathVariable Long horseId,
            @RequestBody HealthReleaseRequest request) {
        return ApiResponse.success("复训放行已确认：全部未闭环事件关闭，马匹恢复在役",
                healthEventService.release(horseId, request));
    }
}
