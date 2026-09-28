package com.equestrian.club.dto;

/** 入栏请求体：把某匹马放进某个栏位 */
public class OccupyRequest {

    private Long horseId;

    public Long getHorseId() {
        return horseId;
    }

    public void setHorseId(Long horseId) {
        this.horseId = horseId;
    }
}
