package com.equestrian.club.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.equestrian.club.common.ApiResponse;
import com.equestrian.club.dto.OperatorRequest;
import com.equestrian.club.dto.SessionRequest;
import com.equestrian.club.dto.view.CoachView;
import com.equestrian.club.dto.view.LessonView;
import com.equestrian.club.dto.view.SessionView;
import com.equestrian.club.service.SessionService;

/** 模块三（骑乘课程与排期）：课程 / 教练字典 + 排期列表、新建排期、取消排期 */
@RestController
@RequestMapping("/api")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping("/lessons")
    public ApiResponse<List<LessonView>> lessons() {
        return ApiResponse.success(sessionService.listLessons());
    }

    @GetMapping("/coaches")
    public ApiResponse<List<CoachView>> coaches() {
        return ApiResponse.success(sessionService.listCoaches());
    }

    @GetMapping("/sessions")
    public ApiResponse<List<SessionView>> sessions() {
        return ApiResponse.success(sessionService.list());
    }

    @GetMapping("/sessions/{id}")
    public ApiResponse<SessionView> detail(@PathVariable Long id) {
        return ApiResponse.success(sessionService.detail(id));
    }

    @PostMapping("/sessions")
    public ApiResponse<SessionView> create(@RequestBody SessionRequest request) {
        return ApiResponse.success("排期已创建", sessionService.create(request));
    }

    @PostMapping("/sessions/{id}/cancel")
    public ApiResponse<SessionView> cancel(@PathVariable Long id,
            @RequestBody(required = false) OperatorRequest request) {
        String operatorName = request == null ? null : request.getOperatorName();
        return ApiResponse.success("排期已取消", sessionService.cancel(id, operatorName));
    }
}
