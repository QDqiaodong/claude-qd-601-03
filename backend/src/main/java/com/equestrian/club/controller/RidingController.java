package com.equestrian.club.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.equestrian.club.common.ApiResponse;
import com.equestrian.club.dto.RidingRequest;
import com.equestrian.club.dto.view.MemberView;
import com.equestrian.club.dto.view.RecordView;
import com.equestrian.club.service.RidingService;

/** 模块四（会员与骑乘记录）：会员列表 / 骑乘记录列表 / 预约 / 取消 / 完成 */
@RestController
@RequestMapping("/api")
public class RidingController {

    private final RidingService ridingService;

    public RidingController(RidingService ridingService) {
        this.ridingService = ridingService;
    }

    @GetMapping("/members")
    public ApiResponse<List<MemberView>> members() {
        return ApiResponse.success(ridingService.listMembers());
    }

    @GetMapping("/members/{id}/records")
    public ApiResponse<List<RecordView>> memberRecords(@PathVariable Long id) {
        return ApiResponse.success(ridingService.recordsOfMember(id));
    }

    @GetMapping("/riding-records")
    public ApiResponse<List<RecordView>> records() {
        return ApiResponse.success(ridingService.listRecords());
    }

    @GetMapping("/riding-records/by-session/{sessionId}")
    public ApiResponse<List<RecordView>> sessionRecords(@PathVariable Long sessionId) {
        return ApiResponse.success(ridingService.recordsOfSession(sessionId));
    }

    @PostMapping("/riding-records")
    public ApiResponse<RecordView> book(@RequestBody RidingRequest request) {
        return ApiResponse.success("预约成功", ridingService.book(request));
    }

    @PostMapping("/riding-records/{id}/cancel")
    public ApiResponse<RecordView> cancel(@PathVariable Long id) {
        return ApiResponse.success("预约已取消", ridingService.cancel(id));
    }

    @PostMapping("/riding-records/{id}/complete")
    public ApiResponse<RecordView> complete(@PathVariable Long id) {
        return ApiResponse.success("骑乘已完成", ridingService.complete(id));
    }
}
