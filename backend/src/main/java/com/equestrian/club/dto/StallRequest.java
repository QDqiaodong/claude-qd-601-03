package com.equestrian.club.dto;

import java.math.BigDecimal;

/** 栏位新增 / 修改请求体 */
public class StallRequest {

    private String stallNo;
    private String barnName;
    private BigDecimal areaSqm;
    private String status;

    public String getStallNo() {
        return stallNo;
    }

    public void setStallNo(String stallNo) {
        this.stallNo = stallNo;
    }

    public String getBarnName() {
        return barnName;
    }

    public void setBarnName(String barnName) {
        this.barnName = barnName;
    }

    public BigDecimal getAreaSqm() {
        return areaSqm;
    }

    public void setAreaSqm(BigDecimal areaSqm) {
        this.areaSqm = areaSqm;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
