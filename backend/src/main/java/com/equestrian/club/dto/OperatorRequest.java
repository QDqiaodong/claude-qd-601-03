package com.equestrian.club.dto;

/**
 * 健康事件写操作的公共身份字段：谁（operatorName）以什么角色（operatorRole）操作。
 *
 * <p>角色不是只靠前端隐藏按钮：每个写接口都在后端再校验一次，
 * 普通工作人员（STAFF）即使直接调接口也无法完成复训放行（只有 MANAGER 可以）。
 */
public class OperatorRequest {

    /** 操作人姓名 */
    private String operatorName;

    /** STAFF 普通工作人员 / MANAGER 负责人 */
    private String operatorRole;

    /**
     * 前端为「一次点击」生成的幂等键。重复点击 / 网络重试携带同一个 requestId，
     * 后端只落一条记录、只联动一次马匹状态。
     */
    private String requestId;

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    public String getOperatorRole() {
        return operatorRole;
    }

    public void setOperatorRole(String operatorRole) {
        this.operatorRole = operatorRole;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
