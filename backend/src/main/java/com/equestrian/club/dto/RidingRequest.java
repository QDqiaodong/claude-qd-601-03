package com.equestrian.club.dto;

/** 骑乘记录预约请求体 */
public class RidingRequest {

    private Long memberId;
    /** 必须指向一条已排期的课程场次 */
    private Long sessionId;
    /** 可空：不传则用排期自带的马匹 */
    private Long horseId;
    private String remark;

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public Long getHorseId() {
        return horseId;
    }

    public void setHorseId(Long horseId) {
        this.horseId = horseId;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
