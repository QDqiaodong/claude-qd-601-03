package com.equestrian.club.dict;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 马术俱乐部业务字典与规则参数表。
 * 状态取值、状态机许可边、时段表、会员折扣等规则集中在此，便于按题面调整或扩展。
 */
public final class EquestrianDict {

    private EquestrianDict() {
    }

    // ---------------- 马匹在役状态机 ----------------
    public static final String HORSE_ACTIVE = "ACTIVE";
    public static final String HORSE_RESTING = "RESTING";
    public static final String HORSE_RETIRED = "RETIRED";

    private static final Map<String, String> HORSE_STATUS = Map.of(
            HORSE_ACTIVE, "在役",
            HORSE_RESTING, "休养",
            HORSE_RETIRED, "退役");

    /** 状态机许可边：退役是终态，不可回退 */
    private static final Map<String, List<String>> HORSE_STATUS_EDGE = Map.of(
            HORSE_ACTIVE, List.of(HORSE_RESTING, HORSE_RETIRED),
            HORSE_RESTING, List.of(HORSE_ACTIVE, HORSE_RETIRED),
            HORSE_RETIRED, List.of());

    // ---------------- 马匹骑乘等级 ----------------
    private static final Map<String, String> RIDE_LEVEL = Map.of(
            "BEGINNER_SAFE", "初级安全马",
            "INTERMEDIATE", "中级马",
            "ADVANCED", "高级马");

    // ---------------- 栏位状态 ----------------
    public static final String STALL_IDLE = "IDLE";
    public static final String STALL_OCCUPIED = "OCCUPIED";
    public static final String STALL_MAINTENANCE = "MAINTENANCE";

    private static final Map<String, String> STALL_STATUS = Map.of(
            STALL_IDLE, "空闲",
            STALL_OCCUPIED, "占用",
            STALL_MAINTENANCE, "维护中");

    // ---------------- 教练 ----------------
    private static final Map<String, String> COACH_LEVEL = Map.of(
            "JUNIOR", "初级教练",
            "SENIOR", "高级教练",
            "CHIEF", "总教练");

    public static final String COACH_ON_DUTY = "ON_DUTY";

    private static final Map<String, String> COACH_STATUS = Map.of(
            COACH_ON_DUTY, "在岗",
            "LEAVE", "休假");

    // ---------------- 课程 ----------------
    public static final String CATEGORY_BASIC = "BASIC";
    public static final String CATEGORY_ADVANCED = "ADVANCED";
    public static final String CATEGORY_PRIVATE = "PRIVATE";

    private static final Map<String, String> LESSON_CATEGORY = Map.of(
            CATEGORY_BASIC, "初级课",
            CATEGORY_ADVANCED, "进阶课",
            CATEGORY_PRIVATE, "私教课");

    public static final String LESSON_ON = "ON";

    private static final Map<String, String> LESSON_STATUS = Map.of(
            LESSON_ON, "上架",
            "OFF", "下架");

    // ---------------- 排期状态 ----------------
    public static final String SESSION_SCHEDULED = "SCHEDULED";
    public static final String SESSION_FULL = "FULL";
    public static final String SESSION_CANCELED = "CANCELED";

    private static final Map<String, String> SESSION_STATUS = Map.of(
            SESSION_SCHEDULED, "已排期",
            SESSION_FULL, "已满员",
            SESSION_CANCELED, "已取消");

    // ---------------- 会员 ----------------
    private static final Map<String, String> MEMBER_LEVEL = Map.of(
            "NORMAL", "普通会员",
            "SILVER", "银卡会员",
            "GOLD", "金卡会员",
            "DIAMOND", "钻石卡会员");

    private static final Map<String, BigDecimal> MEMBER_DISCOUNT = Map.of(
            "NORMAL", BigDecimal.valueOf(1.00),
            "SILVER", BigDecimal.valueOf(0.95),
            "GOLD", BigDecimal.valueOf(0.90),
            "DIAMOND", BigDecimal.valueOf(0.80));

    public static final String MEMBER_NORMAL = "NORMAL";

    private static final Map<String, String> MEMBER_STATUS = Map.of(
            MEMBER_NORMAL, "正常",
            "FROZEN", "冻结");

    // ---------------- 骑乘记录状态 ----------------
    public static final String RECORD_BOOKED = "BOOKED";
    public static final String RECORD_COMPLETED = "COMPLETED";
    public static final String RECORD_CANCELED = "CANCELED";

    private static final Map<String, String> RECORD_STATUS = Map.of(
            RECORD_BOOKED, "已预约",
            RECORD_COMPLETED, "已完成",
            RECORD_CANCELED, "已取消");

    // ---------------- 健康事件状态机 ----------------
    /** 待处理 */
    public static final String EVENT_PENDING = "PENDING";
    /** 观察中 */
    public static final String EVENT_OBSERVING = "OBSERVING";
    /** 待复查 */
    public static final String EVENT_REVIEW_PENDING = "REVIEW_PENDING";
    /** 已关闭（终态，只能由负责人复训放行时系统关闭） */
    public static final String EVENT_CLOSED = "CLOSED";

    private static final Map<String, String> EVENT_STATUS = Map.of(
            EVENT_PENDING, "待处理",
            EVENT_OBSERVING, "观察中",
            EVENT_REVIEW_PENDING, "待复查",
            EVENT_CLOSED, "已关闭");

    /**
     * 事件状态机许可边（人工流转部分）。
     * 已关闭是终态；待复查可退回观察中（复查结论为继续观察）；
     * 待复查 -> 已关闭只能走「负责人复训放行」，不开放给普通流转接口。
     */
    private static final Map<String, List<String>> EVENT_STATUS_EDGE = Map.of(
            EVENT_PENDING, List.of(EVENT_OBSERVING, EVENT_REVIEW_PENDING),
            EVENT_OBSERVING, List.of(EVENT_REVIEW_PENDING),
            EVENT_REVIEW_PENDING, List.of(EVENT_OBSERVING),
            EVENT_CLOSED, List.of());

    // ---------------- 健康事件严重程度 ----------------
    public static final String SEVERITY_HIGH = "HIGH";
    public static final String SEVERITY_MEDIUM = "MEDIUM";
    public static final String SEVERITY_LOW = "LOW";

    private static final Map<String, String> SEVERITY = Map.of(
            SEVERITY_HIGH, "高风险",
            SEVERITY_MEDIUM, "中风险",
            SEVERITY_LOW, "低风险");

    // ---------------- 健康事件日志动作 ----------------
    public static final String ACTION_REGISTER = "REGISTER";
    public static final String ACTION_SUPPLEMENT = "SUPPLEMENT";
    public static final String ACTION_TRANSITION = "TRANSITION";
    public static final String ACTION_REVIEW = "REVIEW";
    public static final String ACTION_RELEASE = "RELEASE";

    private static final Map<String, String> ACTION_NAME = Map.of(
            ACTION_REGISTER, "登记",
            ACTION_SUPPLEMENT, "补充处置",
            ACTION_TRANSITION, "状态流转",
            ACTION_REVIEW, "复查",
            ACTION_RELEASE, "复训放行");

    // ---------------- 复查结论 ----------------
    /** 继续观察（回到观察中，需给下次复查日） */
    public static final String REVIEW_OBSERVE = "OBSERVE";
    /** 调整下次复查日（留在待复查） */
    public static final String REVIEW_RESCHEDULE = "RESCHEDULE";
    /** 复查合格，申请复训放行（留在待复查，等负责人确认） */
    public static final String REVIEW_PASS = "PASS";

    private static final Map<String, String> REVIEW_RESULT = Map.of(
            REVIEW_OBSERVE, "继续观察",
            REVIEW_RESCHEDULE, "调整下次复查日",
            REVIEW_PASS, "复查合格·申请放行");

    // ---------------- 操作人员角色 ----------------
    /** 普通工作人员：可登记、补充、流转、提交复查 */
    public static final String ROLE_STAFF = "STAFF";
    /** 负责人：唯一能确认复训放行的角色 */
    public static final String ROLE_MANAGER = "MANAGER";

    private static final Map<String, String> ROLE_NAME = Map.of(
            ROLE_STAFF, "工作人员",
            ROLE_MANAGER, "负责人");

    // ---------------- 值班班次状态 ----------------
    public static final String SHIFT_OPEN = "OPEN";
    public static final String SHIFT_CLOSED = "CLOSED";

    private static final Map<String, String> SHIFT_STATUS = Map.of(
            SHIFT_OPEN, "进行中",
            SHIFT_CLOSED, "已结束");

    // ---------------- 交接事项状态机 ----------------
    /** 待承接：未分派或已分派未确认；班次结束不消失，继续挂在下一班首页 */
    public static final String ITEM_PENDING = "PENDING";
    /** 已承接（终态）：由目标班次负责人逐项确认后进入，任何旧班次不能改回 */
    public static final String ITEM_ACCEPTED = "ACCEPTED";

    private static final Map<String, String> ITEM_STATUS = Map.of(
            ITEM_PENDING, "待承接",
            ITEM_ACCEPTED, "已承接");

    // ---------------- 交接事项优先级 ----------------
    public static final String PRIORITY_HIGH = "HIGH";
    public static final String PRIORITY_NORMAL = "NORMAL";
    public static final String PRIORITY_LOW = "LOW";

    private static final Map<String, String> ITEM_PRIORITY = Map.of(
            PRIORITY_HIGH, "高",
            PRIORITY_NORMAL, "普通",
            PRIORITY_LOW, "低");

    // ---------------- 交接事项来源类型 ----------------
    public static final String ITEM_TYPE_SESSION = "SESSION";
    public static final String ITEM_TYPE_HORSE = "HORSE";
    public static final String ITEM_TYPE_STALL = "STALL";
    public static final String ITEM_TYPE_MANUAL = "MANUAL";

    private static final Map<String, String> ITEM_TYPE = Map.of(
            ITEM_TYPE_SESSION, "课程",
            ITEM_TYPE_HORSE, "马匹",
            ITEM_TYPE_STALL, "栏位",
            ITEM_TYPE_MANUAL, "手工");

    // ---------------- 交接事项引用类型（去重键的一部分） ----------------
    /** 课程变更（新排期 / 取消 / 健康受影响 / 满员） */
    public static final String REF_SESSION_CHANGE = "SESSION_CHANGE";
    /** 马匹状态（休养待复查等） */
    public static final String REF_HORSE_STATUS = "HORSE_STATUS";
    /** 栏位维护中 */
    public static final String REF_STALL_MAINT = "STALL_MAINT";
    /** 手工补充 */
    public static final String REF_MANUAL = "MANUAL";

    // ---------------- 课程变更细分 ----------------
    /** 当班课程（开班快照定格的今日场次） */
    public static final String CHANGE_SCHEDULED = "SCHEDULED";
    public static final String CHANGE_NEW = "NEW";
    public static final String CHANGE_CANCELED = "CANCELED";
    public static final String CHANGE_AFFECTED = "AFFECTED";
    public static final String CHANGE_FULL = "FULL";

    private static final Map<String, String> CHANGE_KIND = Map.of(
            CHANGE_SCHEDULED, "当班课程",
            CHANGE_NEW, "新排期",
            CHANGE_CANCELED, "取消排期",
            CHANGE_AFFECTED, "健康受影响",
            CHANGE_FULL, "已满员");

    // ---------------- 交接事项操作流水动作 ----------------
    public static final String ITEM_LOG_CREATE = "CREATE";
    public static final String ITEM_LOG_SUPPLEMENT = "SUPPLEMENT";
    public static final String ITEM_LOG_PRIORITY = "PRIORITY";
    public static final String ITEM_LOG_ASSIGN = "ASSIGN";
    public static final String ITEM_LOG_DISPATCH = "DISPATCH";
    public static final String ITEM_LOG_ACCEPT = "ACCEPT";
    public static final String ITEM_LOG_DELAY = "DELAY";

    private static final Map<String, String> ITEM_LOG_ACTION = Map.of(
            ITEM_LOG_CREATE, "建项",
            ITEM_LOG_SUPPLEMENT, "补充说明",
            ITEM_LOG_PRIORITY, "调整优先级",
            ITEM_LOG_ASSIGN, "分派负责人",
            ITEM_LOG_DISPATCH, "分派班次",
            ITEM_LOG_ACCEPT, "承接确认",
            ITEM_LOG_DELAY, "记录延期");

    // ---------------- 训练日历时段 ----------------
    /** 训练日历固定时段起点（整点，每次一小时） */
    public static final List<String> TIME_SLOTS = List.of(
            "09:00", "10:00", "11:00", "14:00", "15:00", "16:00");

    /** 日历默认展示天数 */
    public static final int CALENDAR_DAYS = 7;

    /** 日历最多展示天数 */
    public static final int CALENDAR_MAX_DAYS = 14;

    // ---------------- 取值方法 ----------------

    public static boolean isValidEventStatus(String status) {
        return status != null && EVENT_STATUS.containsKey(status);
    }

    public static String eventStatusName(String status) {
        return status == null ? "" : EVENT_STATUS.getOrDefault(status, status);
    }

    public static List<String> eventStatusTargets(String status) {
        return EVENT_STATUS_EDGE.getOrDefault(status, List.of());
    }

    public static boolean canTransferEventStatus(String from, String to) {
        return eventStatusTargets(from).contains(to);
    }

    /** 未关闭事件（需要继续追踪 / 参与复训放行判定的事件） */
    public static boolean isOpenEvent(String status) {
        return EVENT_PENDING.equals(status) || EVENT_OBSERVING.equals(status)
                || EVENT_REVIEW_PENDING.equals(status);
    }

    public static boolean isValidSeverity(String severity) {
        return severity != null && SEVERITY.containsKey(severity);
    }

    public static String severityName(String severity) {
        return severity == null ? "" : SEVERITY.getOrDefault(severity, severity);
    }

    /** 只有高风险事件才立即强制休养并冻结排课 */
    public static boolean isHighSeverity(String severity) {
        return SEVERITY_HIGH.equals(severity);
    }

    public static String actionName(String action) {
        return action == null ? "" : ACTION_NAME.getOrDefault(action, action);
    }

    public static boolean isValidReviewResult(String result) {
        return result != null && REVIEW_RESULT.containsKey(result);
    }

    public static String reviewResultName(String result) {
        return result == null ? "" : REVIEW_RESULT.getOrDefault(result, result);
    }

    public static boolean isValidRole(String role) {
        return role != null && ROLE_NAME.containsKey(role);
    }

    public static String roleName(String role) {
        return role == null ? "" : ROLE_NAME.getOrDefault(role, role);
    }

    public static boolean isValidShiftStatus(String status) {
        return status != null && SHIFT_STATUS.containsKey(status);
    }

    public static String shiftStatusName(String status) {
        return status == null ? "" : SHIFT_STATUS.getOrDefault(status, status);
    }

    public static boolean isValidItemStatus(String status) {
        return status != null && ITEM_STATUS.containsKey(status);
    }

    public static String itemStatusName(String status) {
        return status == null ? "" : ITEM_STATUS.getOrDefault(status, status);
    }

    public static boolean isValidItemPriority(String priority) {
        return priority != null && ITEM_PRIORITY.containsKey(priority);
    }

    public static String itemPriorityName(String priority) {
        return priority == null ? "" : ITEM_PRIORITY.getOrDefault(priority, priority);
    }

    public static boolean isValidItemType(String itemType) {
        return itemType != null && ITEM_TYPE.containsKey(itemType);
    }

    public static String itemTypeName(String itemType) {
        return itemType == null ? "" : ITEM_TYPE.getOrDefault(itemType, itemType);
    }

    public static String changeKindName(String changeKind) {
        return changeKind == null ? "" : CHANGE_KIND.getOrDefault(changeKind, changeKind);
    }

    public static String itemLogActionName(String action) {
        return action == null ? "" : ITEM_LOG_ACTION.getOrDefault(action, action);
    }

    public static boolean isValidHorseStatus(String status) {
        return status != null && HORSE_STATUS.containsKey(status);
    }

    public static String horseStatusName(String status) {
        return status == null ? "" : HORSE_STATUS.getOrDefault(status, status);
    }

    public static List<String> horseStatusTargets(String status) {
        return HORSE_STATUS_EDGE.getOrDefault(status, List.of());
    }

    public static boolean canTransferHorseStatus(String from, String to) {
        return horseStatusTargets(from).contains(to);
    }

    public static boolean isValidRideLevel(String level) {
        return level != null && RIDE_LEVEL.containsKey(level);
    }

    public static String rideLevelName(String level) {
        return level == null ? "" : RIDE_LEVEL.getOrDefault(level, level);
    }

    public static boolean isValidStallStatus(String status) {
        return status != null && STALL_STATUS.containsKey(status);
    }

    public static String stallStatusName(String status) {
        return status == null ? "" : STALL_STATUS.getOrDefault(status, status);
    }

    public static boolean isValidCoachStatus(String status) {
        return status != null && COACH_STATUS.containsKey(status);
    }

    public static String coachStatusName(String status) {
        return status == null ? "" : COACH_STATUS.getOrDefault(status, status);
    }

    public static String coachLevelName(String level) {
        return level == null ? "" : COACH_LEVEL.getOrDefault(level, level);
    }

    public static boolean isValidCategory(String category) {
        return category != null && LESSON_CATEGORY.containsKey(category);
    }

    public static String categoryName(String category) {
        return category == null ? "" : LESSON_CATEGORY.getOrDefault(category, category);
    }

    public static boolean isAdvancedCategory(String category) {
        return CATEGORY_ADVANCED.equals(category) || CATEGORY_PRIVATE.equals(category);
    }

    public static boolean isValidLessonStatus(String status) {
        return status != null && LESSON_STATUS.containsKey(status);
    }

    public static String lessonStatusName(String status) {
        return status == null ? "" : LESSON_STATUS.getOrDefault(status, status);
    }

    public static boolean isValidSessionStatus(String status) {
        return status != null && SESSION_STATUS.containsKey(status);
    }

    public static String sessionStatusName(String status) {
        return status == null ? "" : SESSION_STATUS.getOrDefault(status, status);
    }

    public static boolean isValidMemberLevel(String level) {
        return level != null && MEMBER_LEVEL.containsKey(level);
    }

    public static String memberLevelName(String level) {
        return level == null ? "" : MEMBER_LEVEL.getOrDefault(level, level);
    }

    public static BigDecimal memberDiscount(String level) {
        return MEMBER_DISCOUNT.getOrDefault(level, BigDecimal.valueOf(1.00));
    }

    public static boolean isValidMemberStatus(String status) {
        return status != null && MEMBER_STATUS.containsKey(status);
    }

    public static String memberStatusName(String status) {
        return status == null ? "" : MEMBER_STATUS.getOrDefault(status, status);
    }

    public static boolean isValidRecordStatus(String status) {
        return status != null && RECORD_STATUS.containsKey(status);
    }

    public static String recordStatusName(String status) {
        return status == null ? "" : RECORD_STATUS.getOrDefault(status, status);
    }

    public static boolean isValidTimeSlot(String startTime) {
        return startTime != null && TIME_SLOTS.contains(startTime);
    }

    /**
     * 依据整点起始时间推出一小时后的结束时间，如 09:00 -> 10:00
     */
    public static String endOfSlot(String startTime) {
        if (startTime == null || startTime.length() != 5) {
            return null;
        }
        int hour;
        try {
            hour = Integer.parseInt(startTime.substring(0, 2));
        } catch (NumberFormatException e) {
            return null;
        }
        return String.format("%02d:%s", (hour + 1) % 24, startTime.substring(3));
    }

    /**
     * 判断两个 [start, end) 时段是否重叠（同一天的 HH:mm 字符串直接比较即可）
     */
    public static boolean overlap(String startA, String endA, String startB, String endB) {
        if (startA == null || endA == null || startB == null || endB == null) {
            return false;
        }
        return startA.compareTo(endB) < 0 && endA.compareTo(startB) > 0;
    }
}
