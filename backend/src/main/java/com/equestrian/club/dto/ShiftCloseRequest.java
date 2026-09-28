package com.equestrian.club.dto;

import java.util.List;

/**
 * 结束班次。只有来源班次负责人（MANAGER）可以结束。
 *
 * <p>仍有未承接事项时不能直接关班：已分派未承接的逐项给延期原因、
 * 未分派的逐项给延期原因（系统自动派给下一班）。
 * 每个延期原因与事项一一对应，校验在事务内逐项完成——任何一项不满足，
 * 整个关班回滚，不留下「班次已结束但事项漏派 / 半条延期」的中间态。
 */
public class ShiftCloseRequest extends OperatorRequest {

    /** 页面看到的班次清单版本，落后于库里当前版本直接 409 */
    private Long expectedVersion;

    /** 未承接事项的逐项延期原因（itemId + 必填 reason） */
    private List<DelayItem> delays;

    public Long getExpectedVersion() {
        return expectedVersion;
    }

    public void setExpectedVersion(Long expectedVersion) {
        this.expectedVersion = expectedVersion;
    }

    public List<DelayItem> getDelays() {
        return delays;
    }

    public void setDelays(List<DelayItem> delays) {
        this.delays = delays;
    }

    public static class DelayItem {

        private Long itemId;

        /** 延期原因，必填 */
        private String reason;

        public Long getItemId() {
            return itemId;
        }

        public void setItemId(Long itemId) {
            this.itemId = itemId;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }
    }
}
