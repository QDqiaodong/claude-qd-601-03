package com.equestrian.club.event;

/**
 * 课程变更事件：排期新建 / 取消成功后在同一事务内发布（BEFORE_COMMIT）。
 *
 * <p>交接服务监听后把变更挂入「当前最新 OPEN 班次」的交接清单，
 * 与开班快照共用未承接事项唯一键去重。事件与业务库写在同一事务里：
 * 挂入失败则排期操作一起回滚，不会留下「课程改了但交接清单没有」的半条记录。
 *
 * @param sessionId  排期 id
 * @param changeKind NEW 新排期 / CANCELED 取消排期
 * @param operator   操作人（可空，取「值班台」兜底）
 */
public record SessionChangedEvent(Long sessionId, String changeKind, String operator) {
}
