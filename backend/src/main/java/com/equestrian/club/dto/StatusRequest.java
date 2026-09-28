package com.equestrian.club.dto;

/** 通用的单状态字段请求体（如马匹状态机流转） */
public class StatusRequest {

    private String status;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
