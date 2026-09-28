package com.equestrian.club.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 登记健康事件请求体。
 * 至少包含：发生时间、严重程度、症状说明、处置建议、预计复查日。
 */
public class HealthEventCreateRequest extends OperatorRequest {

    private Long horseId;

    /** 发生时间，不传取当前时间 */
    private LocalDateTime occurredAt;

    /** HIGH 高风险 / MEDIUM 中风险 / LOW 低风险 */
    private String severity;

    private String symptom;

    private String treatmentAdvice;

    /** 预计复查日 */
    private LocalDate expectedReviewDate;

    public Long getHorseId() {
        return horseId;
    }

    public void setHorseId(Long horseId) {
        this.horseId = horseId;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getSymptom() {
        return symptom;
    }

    public void setSymptom(String symptom) {
        this.symptom = symptom;
    }

    public String getTreatmentAdvice() {
        return treatmentAdvice;
    }

    public void setTreatmentAdvice(String treatmentAdvice) {
        this.treatmentAdvice = treatmentAdvice;
    }

    public LocalDate getExpectedReviewDate() {
        return expectedReviewDate;
    }

    public void setExpectedReviewDate(LocalDate expectedReviewDate) {
        this.expectedReviewDate = expectedReviewDate;
    }
}
