package com.equestrian.club.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.equestrian.club.common.ApiResponse;
import com.equestrian.club.dto.OccupyRequest;
import com.equestrian.club.dto.StallRequest;
import com.equestrian.club.dto.view.StallBoardView;
import com.equestrian.club.dto.view.StallView;
import com.equestrian.club.service.StallService;

/** 模块二（马房与栏位）：看板快照 / 列表 / 新增 / 局部修改 / 入栏 / 出栏 / 维护 */
@RestController
@RequestMapping("/api/stalls")
public class StallController {

    private final StallService stallService;

    public StallController(StallService stallService) {
        this.stallService = stallService;
    }

    /**
     * 看板快照：顶部汇总、马房分组、栏位明细在后端同一只读事务里一次算出。
     * 前端整包替换展示状态，三处数字必然来自同一批数据。
     */
    @GetMapping("/board")
    public ApiResponse<StallBoardView> board() {
        return ApiResponse.success(stallService.board());
    }

    @GetMapping
    public ApiResponse<List<StallView>> list() {
        return ApiResponse.success(stallService.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<StallView> detail(@PathVariable Long id) {
        return ApiResponse.success(stallService.detail(id));
    }

    @PostMapping
    public ApiResponse<StallView> create(@RequestBody StallRequest request) {
        return ApiResponse.success("栏位已创建", stallService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<StallView> update(@PathVariable Long id, @RequestBody StallRequest request) {
        return ApiResponse.success("栏位已更新", stallService.update(id, request));
    }

    @PostMapping("/{id}/occupy")
    public ApiResponse<StallView> occupy(@PathVariable Long id, @RequestBody OccupyRequest request) {
        return ApiResponse.success("入栏成功", stallService.occupy(id, request));
    }

    @PostMapping("/{id}/release")
    public ApiResponse<StallView> release(@PathVariable Long id) {
        return ApiResponse.success("出栏成功", stallService.release(id));
    }

    @PostMapping("/{id}/maintenance")
    public ApiResponse<StallView> maintenance(@PathVariable Long id) {
        return ApiResponse.success("栏位已转入维护", stallService.maintenance(id));
    }

    @PostMapping("/{id}/restore")
    public ApiResponse<StallView> restore(@PathVariable Long id) {
        return ApiResponse.success("栏位维护完成", stallService.restore(id));
    }
}
