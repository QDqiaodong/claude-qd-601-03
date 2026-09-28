package com.equestrian.club.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.equestrian.club.common.ApiResponse;
import com.equestrian.club.dto.HorseRequest;
import com.equestrian.club.dto.StatusRequest;
import com.equestrian.club.dto.view.HorseCalendarView;
import com.equestrian.club.dto.view.HorseView;
import com.equestrian.club.service.HorseService;

/** 模块一（马匹档案）：列表 / 详情 / 新增 / 局部修改 / 状态机流转 / 训练日历 */
@RestController
@RequestMapping("/api/horses")
public class HorseController {

    private final HorseService horseService;

    public HorseController(HorseService horseService) {
        this.horseService = horseService;
    }

    @GetMapping
    public ApiResponse<List<HorseView>> list() {
        return ApiResponse.success(horseService.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<HorseView> detail(@PathVariable Long id) {
        return ApiResponse.success(horseService.detail(id));
    }

    @GetMapping("/{id}/calendar")
    public ApiResponse<HorseCalendarView> calendar(@PathVariable Long id,
            @RequestParam(required = false) Integer days) {
        return ApiResponse.success(horseService.calendar(id, days));
    }

    @PostMapping
    public ApiResponse<HorseView> create(@RequestBody HorseRequest request) {
        return ApiResponse.success("马匹已建档", horseService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<HorseView> update(@PathVariable Long id, @RequestBody HorseRequest request) {
        return ApiResponse.success("马匹档案已更新", horseService.update(id, request));
    }

    @PostMapping("/{id}/status")
    public ApiResponse<HorseView> changeStatus(@PathVariable Long id, @RequestBody StatusRequest request) {
        return ApiResponse.success("马匹状态已流转", horseService.changeStatus(id, request));
    }
}
