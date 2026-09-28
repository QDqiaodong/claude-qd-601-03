package com.equestrian.club.dto;

import java.time.LocalDate;

/**
 * 复查请求体。
 * 只有「待复查」事件可复查；结论三选一：继续观察 / 调整下次复查日 / 复查合格申请放行。
 */
public class HealthReviewRequest extends OperatorRequest {

    /** 复查日期，不传取当天 */
    private LocalDate reviewDate;

    /** OBSERVE 继续观察 / RESCHEDULE 调整下次复查日 / PASS 复查合格申请放行 */
    private String result;

    /** 复查结论 / 说明，必填 */
    private String conclusion;

    /** 下次复查日：OBSERVE / RESCHEDULE 必填；PASS 可空（空=合格长期有效） */
    private LocalDate nextReviewDate;

    /** 页面打开时看到的马匹健康事件链版本，落后即 409 */
    private Long expectedVersion;

    public LocalDate getReviewDate() {
        return reviewDate;
    }

    public void setReviewDate(LocalDate reviewDate) {
        this.reviewDate = reviewDate;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getConclusion() {
        return conclusion;
    }

    public void setConclusion(String conclusion) {
        this.conclusion = conclusion;
    }

    public LocalDate getNextReviewDate() {
        return nextReviewDate;
    }

    public void setNextReviewDate(LocalDate nextReviewDate) {
        this.nextReviewDate = nextReviewDate;
    }

    public Long getExpectedVersion() {
        return expectedVersion;
    }

    public void setExpectedVersion(Long expectedVersion) {
        this.expectedVersion = expectedVersion;
    }
}
