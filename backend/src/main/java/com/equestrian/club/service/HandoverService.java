package com.equestrian.club.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.equestrian.club.common.BizException;
import com.equestrian.club.common.ConflictException;
import com.equestrian.club.dict.EquestrianDict;
import com.equestrian.club.domain.Coach;
import com.equestrian.club.domain.CoachRepository;
import com.equestrian.club.domain.DutySeqLockRepository;
import com.equestrian.club.domain.DutyShift;
import com.equestrian.club.domain.DutyShiftRepository;
import com.equestrian.club.domain.HandoverAcceptance;
import com.equestrian.club.domain.HandoverAcceptanceRepository;
import com.equestrian.club.domain.HandoverItem;
import com.equestrian.club.domain.HandoverItemLog;
import com.equestrian.club.domain.HandoverItemLogRepository;
import com.equestrian.club.domain.HandoverItemRepository;
import com.equestrian.club.domain.HealthEvent;
import com.equestrian.club.domain.HealthEventRepository;
import com.equestrian.club.domain.Horse;
import com.equestrian.club.domain.HorseRepository;
import com.equestrian.club.domain.Lesson;
import com.equestrian.club.domain.LessonRepository;
import com.equestrian.club.domain.LessonSession;
import com.equestrian.club.domain.LessonSessionRepository;
import com.equestrian.club.domain.Stall;
import com.equestrian.club.domain.StallRepository;
import com.equestrian.club.dto.ItemAcceptRequest;
import com.equestrian.club.dto.ItemCreateRequest;
import com.equestrian.club.dto.ItemDispatchRequest;
import com.equestrian.club.dto.ItemSupplementRequest;
import com.equestrian.club.dto.ItemUpdateRequest;
import com.equestrian.club.dto.OperatorRequest;
import com.equestrian.club.dto.ShiftCloseRequest;
import com.equestrian.club.dto.ShiftCreateRequest;
import com.equestrian.club.dto.ShiftNoteRequest;
import com.equestrian.club.dto.view.AcceptanceView;
import com.equestrian.club.dto.view.CloseBlockerView;
import com.equestrian.club.dto.view.ItemLogView;
import com.equestrian.club.dto.view.ItemView;
import com.equestrian.club.dto.view.SessionHandoverView;
import com.equestrian.club.dto.view.ShiftBriefView;
import com.equestrian.club.dto.view.ShiftView;
import com.equestrian.club.event.SessionChangedEvent;

/**
 * 值班交接与未结事项承接服务。
 *
 * <p>核心链路：负责人开班（系统从当班课程 / 马匹状态 / 栏位安排生成交接清单）
 * → 负责人补充说明 / 标优先级 / 分派下一班 → 下一班负责人逐项确认承接 → 事项进入
 * 已承接（终态）。未确认事项不因班次结束消失，靠 target_shift_id 继续挂在下一班首页。
 *
 * <p>并发与一致性由四层保证：
 * <ol>
 *   <li>开班 / 关班 / 期间课程变更入队先抢全局序号锁（duty_seq_lock 固定一行），
 *       串行化「找最新 OPEN 班次 + 事项生成 / 漏派校验 + 落库」，
 *       课程变更永远进对班次、关班永远不漏派，也不存在多行循环加锁的死锁；</li>
 *   <li>事项用行锁 + expectedVersion 乐观版本，旧页面提交过期内容直接 409，
 *       要求重新载入；班次用 itemVersion 保护关班 / 改总说明；</li>
 *   <li>承接记录唯一约束 + requestId 幂等，重复确认不产生重复记录；
 *       已承接是终态，旧班次不能改回未处理；</li>
 *   <li>关班逐项校验 + 同事务原子落库，任何一项缺延期原因 / 没有下一班，整笔回滚，
 *       不留半条记录。</li>
 * </ol>
 */
@Service
public class HandoverService {

    /** 开班快照向前看的天数（与训练日历口径一致） */
    private static final int SNAPSHOT_DAYS = 7;

    private final DutyShiftRepository shiftRepository;
    private final DutySeqLockRepository seqLockRepository;
    private final HandoverItemRepository itemRepository;
    private final HandoverItemLogRepository logRepository;
    private final HandoverAcceptanceRepository acceptanceRepository;
    private final LessonSessionRepository sessionRepository;
    private final LessonRepository lessonRepository;
    private final CoachRepository coachRepository;
    private final HorseRepository horseRepository;
    private final StallRepository stallRepository;
    private final HealthEventRepository eventRepository;

    public HandoverService(DutyShiftRepository shiftRepository,
            DutySeqLockRepository seqLockRepository,
            HandoverItemRepository itemRepository,
            HandoverItemLogRepository logRepository,
            HandoverAcceptanceRepository acceptanceRepository,
            LessonSessionRepository sessionRepository,
            LessonRepository lessonRepository,
            CoachRepository coachRepository,
            HorseRepository horseRepository,
            StallRepository stallRepository,
            HealthEventRepository eventRepository) {
        this.shiftRepository = shiftRepository;
        this.seqLockRepository = seqLockRepository;
        this.itemRepository = itemRepository;
        this.logRepository = logRepository;
        this.acceptanceRepository = acceptanceRepository;
        this.sessionRepository = sessionRepository;
        this.lessonRepository = lessonRepository;
        this.coachRepository = coachRepository;
        this.horseRepository = horseRepository;
        this.stallRepository = stallRepository;
        this.eventRepository = eventRepository;
    }

    // ============================== 查询 ==============================

    /**
     * 首页交接台：返回最新一个 OPEN 班次（含待承接来项）；
     * 没有进行中班次时返回 null，页面引导负责人开班。
     */
    @Transactional(readOnly = true)
    public ShiftView activeBoard() {
        DutyShift shift = shiftRepository
                .findFirstByStatusOrderByIdDesc(EquestrianDict.SHIFT_OPEN).orElse(null);
        return shift == null ? null : toShiftView(shift, true);
    }

    /** 班次详情：完整清单 + 操作流水 */
    @Transactional(readOnly = true)
    public ShiftView detail(Long shiftId) {
        return toShiftView(requireShift(shiftId), true);
    }

    /**
     * 历史班次查询：按日期（单日或区间）、负责人、事项状态组合筛选。
     */
    @Transactional(readOnly = true)
    public List<ShiftBriefView> history(LocalDate from, LocalDate to, String leaderName, String itemStatus) {
        LocalDate start = from == null ? LocalDate.of(1970, 1, 1) : from;
        LocalDate end = to == null ? LocalDate.of(2999, 12, 31) : to;
        if (start.isAfter(end)) {
            throw new BizException("开始日期不能晚于结束日期");
        }
        String leader = trimToNull(leaderName);
        List<DutyShift> shifts = leader == null
                ? shiftRepository.findByShiftDateBetweenOrderByIdDesc(start, end)
                : shiftRepository.findByShiftDateBetweenAndLeaderNameContainingOrderByIdDesc(start, end, leader);

        // 事项状态过滤：事项由该班产生、或分派给该班，只要命中即保留该班次
        if (itemStatus != null && !itemStatus.isBlank()) {
            if (!EquestrianDict.isValidItemStatus(itemStatus)) {
                throw new BizException("事项状态取值不合法：" + itemStatus);
            }
            List<Long> ids = shifts.stream().map(DutyShift::getId).toList();
            if (ids.isEmpty()) {
                return List.of();
            }
            Set<Long> produced = new HashSet<>(
                    itemRepository.shiftIdsProducedWithStatus(ids, itemStatus));
            Set<Long> incoming = new HashSet<>(
                    itemRepository.shiftIdsIncomingWithStatus(ids, itemStatus));
            shifts = shifts.stream()
                    .filter(s -> produced.contains(s.getId()) || incoming.contains(s.getId()))
                    .toList();
        }
        return shifts.stream().map(this::toBriefView).toList();
    }

    /**
     * 课程详情：它属于哪个班次 + 最近一次承接记录。
     * 取该排期最新一条事项（无论变更细分），展示其来源班次与承接信息。
     */
    @Transactional(readOnly = true)
    public SessionHandoverView sessionHandoverSummary(Long sessionId) {
        if (sessionId == null) {
            return null;
        }
        List<HandoverItem> items = itemRepository
                .findByRefTypeAndRefId(EquestrianDict.REF_SESSION_CHANGE, sessionId);
        if (items.isEmpty()) {
            return null;
        }
        HandoverItem latest = items.stream().max(Comparator.comparing(HandoverItem::getId)).orElseThrow();
        return toSessionHandover(latest);
    }

    // ============================== 开班 ==============================

    /**
     * 负责人建立值班班次。开班即从当班课程 / 马匹状态 / 栏位安排定格交接清单：
     * <ul>
     *   <li>今日全部未取消课程（当班课程）；</li>
     *   <li>未来 {@value #SNAPSHOT_DAYS} 天内满员场次、被健康事件标记受影响场次；</li>
     *   <li>休养中的马匹（各有未闭环健康事件）；</li>
     *   <li>维护中的栏位。</li>
     * </ul>
     * 上一班分派过来的未承接事项不属于本班快照（它们已经以「来项」身份挂在本班）。
     */
    @Transactional
    public ShiftView createShift(ShiftCreateRequest request) {
        if (request == null) {
            throw new BizException("开班失败：请求体为空");
        }
        Operator operator = requireOperator(request);
        if (!EquestrianDict.ROLE_MANAGER.equals(operator.role())) {
            throw new BizException("只有值班负责人才能建立值班班次，当前身份是普通教练");
        }
        // 开班幂等：重复点击只取回同一个班次
        if (operator.requestId() != null) {
            DutyShift idempotent = shiftRepository.findByCreateRequestId(operator.requestId()).orElse(null);
            if (idempotent != null) {
                return detail(idempotent.getId());
            }
        }

        LocalDate date = request.getShiftDate() == null ? LocalDate.now() : request.getShiftDate();
        if (date.isAfter(LocalDate.now().plusDays(1))) {
            throw new BizException("值班日期不能晚于明天");
        }

        // 串行化班次链：与关班 / 课程变更入队互斥
        lockSeq(operator.name());

        // 持锁后再查一次：同一 requestId 的并发开班只开一个班（唯一键兜底）
        if (operator.requestId() != null) {
            DutyShift again = shiftRepository.findByCreateRequestId(operator.requestId()).orElse(null);
            if (again != null) {
                return detail(again.getId());
            }
        }

        DutyShift shift = new DutyShift();
        shift.setShiftDate(date);
        shift.setLeaderName(operator.name());
        shift.setHandoverNote(trimToNull(request.getHandoverNote()));
        shift.setStatus(EquestrianDict.SHIFT_OPEN);
        shift.setItemVersion(0L);
        shift.setStartedAt(LocalDateTime.now());
        shift.setCreateRequestId(operator.requestId());
        // 编号以自增 id 为准，先放唯一占位号，落库后改正式编号 DS-00000x
        shift.setShiftNo("DS-TMP-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        shift = shiftRepository.save(shift);
        long seq = Math.max(shift.getId(),
                shiftRepository.findFirstByOrderByIdDesc().map(DutyShift::getId).orElse(0L));
        shift.setShiftNo(String.format("DS-%06d", seq));
        shift = shiftRepository.save(shift);

        int created = generateSnapshot(shift, operator);
        if (created > 0) {
            shift.setItemVersion((long) created);
            shift = shiftRepository.save(shift);
        }
        return detail(shift.getId());
    }

    // ============================== 期间课程变更 ==============================

    /**
     * 排期新建 / 取消事件（与业务同事务、提交前触发）：把新出现的课程变更
     * 挂入当前最新 OPEN 班次。没有进行中班次时不入清单（无人接班就不存在交接）。
     * 未承接事项唯一键保证与开班快照、重复事件不撞车。
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onSessionChanged(SessionChangedEvent event) {
        if (event == null || event.sessionId() == null) {
            return;
        }
        // 全局锁与开班 / 关班互斥：保证「变更进对班次」
        seqLockRepository.lock();
        DutyShift shift = shiftRepository
                .findFirstByStatusOrderByIdDesc(EquestrianDict.SHIFT_OPEN).orElse(null);
        if (shift == null) {
            return;
        }
        registerSessionChange(shift, event.sessionId(), event.changeKind(),
                trimToNull(event.operator()), null);
        shiftRepository.save(shift);
    }

    // ============================== 手工建项 / 补充 / 改优先级 / 分派 ==============================

    /** 值班负责人手工补充一条交接事项 */
    @Transactional
    public ItemView createItem(Long shiftId, ItemCreateRequest request) {
        if (request == null) {
            throw new BizException("补充事项失败：请求体为空");
        }
        Operator operator = requireOperator(request);
        if (!EquestrianDict.ROLE_MANAGER.equals(operator.role())) {
            throw new BizException("只有值班负责人才能新增交接事项，普通教练请使用「补充说明」");
        }
        String title = trimToNull(request.getTitle());
        if (title == null) {
            throw new BizException("请填写事项标题");
        }
        DutyShift shift = requireOpenShift(shiftId);
        if (!shift.getLeaderName().equals(operator.name())) {
            throw new BizException("只有本班负责人「" + shift.getLeaderName() + "」才能新增本班事项");
        }
        if (operator.requestId() != null) {
            HandoverItemLog existed = logRepository.findByRequestId(operator.requestId()).orElse(null);
            if (existed != null && EquestrianDict.ITEM_LOG_CREATE.equals(existed.getAction())) {
                return itemDetail(existed.getItemId());
            }
        }
        String priority = request.getPriority() == null
                ? EquestrianDict.PRIORITY_NORMAL : request.getPriority();
        if (!EquestrianDict.isValidItemPriority(priority)) {
            throw new BizException("优先级取值不合法：" + priority + "，只支持 HIGH / NORMAL / LOW");
        }

        lockSeq(operator.name());
        shift = requireOpenShift(shiftId);
        HandoverItem item = newItem(shift, EquestrianDict.ITEM_TYPE_MANUAL, EquestrianDict.REF_MANUAL,
                null, null, title, trimToNull(request.getDetail()), priority,
                trimToNull(request.getOwnerName()), operator.requestId());
        item = persistNewItem(item);
        appendLog(item, EquestrianDict.ITEM_LOG_CREATE, "负责人手工新增交接事项：" + title,
                operator, item.getRequestId());
        bumpShiftVersion(shift);
        return itemView(item, true, null);
    }

    /**
     * 补充事项说明。普通教练只能补充分派给自己（owner_name 是本人）的未承接事项；
     * 负责人可补充本班任意未承接事项。已承接事项不能再补充（终态保护）。
     */
    @Transactional
    public ItemView supplement(Long itemId, ItemSupplementRequest request) {
        if (request == null) {
            throw new BizException("补充失败：请求体为空");
        }
        Operator operator = requireOperator(request);
        String note = trimToNull(request.getNote());
        if (note == null) {
            throw new BizException("请填写要补充的说明");
        }
        HandoverItemLog existed = existingIdempotentLog(operator.requestId());
        if (existed != null) {
            return itemDetail(existed.getItemId());
        }

        HandoverItem item = lockItem(itemId);
        assertItemVersion(item, request.getExpectedVersion());
        requirePending(item, "补充说明");
        DutyShift source = shiftRepository.findById(item.getShiftId())
                .orElseThrow(() -> new BizException("来源班次不存在，编号：" + item.getShiftId()));

        boolean manager = EquestrianDict.ROLE_MANAGER.equals(operator.role());
        boolean own = item.getOwnerName() != null && item.getOwnerName().equals(operator.name());
        boolean sourceLeader = manager && source.getLeaderName().equals(operator.name());
        boolean targetLeader = false;
        DutyShift targetShift = null;
        if (item.getTargetShiftId() != null) {
            targetShift = shiftRepository.findById(item.getTargetShiftId()).orElse(null);
            targetLeader = manager && targetShift != null
                    && targetShift.getLeaderName().equals(operator.name());
        }
        if (!manager) {
            if (!own) {
                throw new BizException(item.getOwnerName() == null
                        ? "该事项还没有分派负责教练，普通教练只能补充分派给自己的事项"
                        : "该事项分派给教练「" + item.getOwnerName() + "」，普通教练只能补充自己负责的事项");
            }
        } else if (!sourceLeader && !targetLeader && !own) {
            // 负责人也只能动「自己负责的班次」上的事项，别班负责人不能越权
            throw new BizException("事项属于班次「" + source.getShiftNo() + "」（负责人 "
                    + source.getLeaderName() + "）"
                    + (targetShift == null ? "" : "，承接班次负责人为 " + targetShift.getLeaderName())
                    + "，你不是对应班次负责人也不是事项负责教练，无权补充");
        }

        item.setExpectedVersion(item.getExpectedVersion() + 1);
        itemRepository.save(item);
        appendLog(item, EquestrianDict.ITEM_LOG_SUPPLEMENT, note, operator, operator.requestId());
        bumpVersionsForItemChange(item, source);
        return itemDetail(item.getId());
    }

    /** 值班负责人调整优先级 / 分派负责教练 */
    @Transactional
    public ItemView updateItem(Long itemId, ItemUpdateRequest request) {
        if (request == null) {
            throw new BizException("更新失败：请求体为空");
        }
        Operator operator = requireManager(request);
        HandoverItem item = lockItem(itemId);
        assertItemVersion(item, request.getExpectedVersion());
        requirePending(item, "调整");
        DutyShift source = requireOpenShift(item.getShiftId());
        requireSourceLeader(source, operator);

        StringBuilder note = new StringBuilder();
        boolean ownerChanged = false;
        if (request.getPriority() != null) {
            String priority = request.getPriority();
            if (!EquestrianDict.isValidItemPriority(priority)) {
                throw new BizException("优先级取值不合法：" + priority + "，只支持 HIGH / NORMAL / LOW");
            }
            if (!priority.equals(item.getPriority())) {
                note.append("优先级：")
                        .append(EquestrianDict.itemPriorityName(item.getPriority()))
                        .append(" → ")
                        .append(EquestrianDict.itemPriorityName(priority))
                        .append("；");
                item.setPriority(priority);
            }
        }
        // ownerName：null=不改；空串=清除分派；非空=改派
        if (request.getOwnerName() != null) {
            String owner = trimToNull(request.getOwnerName());
            String oldOwner = item.getOwnerName();
            if (!Objects.equals(oldOwner, owner)) {
                ownerChanged = true;
                note.append("负责教练：").append(oldOwner == null ? "未分派" : oldOwner)
                        .append(" → ").append(owner == null ? "未分派" : owner).append("；");
                item.setOwnerName(owner);
            }
        }
        if (note.length() == 0) {
            // 没有实际变化也不能算一次新提交：幂等返回当前状态
            return itemDetail(item.getId());
        }
        // 以「是否分派了负责人」决定流水动作
        String action = ownerChanged ? EquestrianDict.ITEM_LOG_ASSIGN : EquestrianDict.ITEM_LOG_PRIORITY;
        item.setExpectedVersion(item.getExpectedVersion() + 1);
        itemRepository.save(item);
        appendLog(item, action, note.toString(), operator, operator.requestId());
        bumpVersionsForItemChange(item, source);
        return itemDetail(item.getId());
    }

    /** 值班负责人把事项分派给下一班 */
    @Transactional
    public ItemView dispatch(Long itemId, ItemDispatchRequest request) {
        if (request == null) {
            throw new BizException("分派失败：请求体为空");
        }
        Operator operator = requireManager(request);
        HandoverItem item = lockItem(itemId);
        assertItemVersion(item, request.getExpectedVersion());
        requirePending(item, "分派");
        DutyShift source = requireOpenShift(item.getShiftId());
        requireSourceLeader(source, operator);

        DutyShift target;
        if (request.getTargetShiftId() != null) {
            target = requireOpenShift(request.getTargetShiftId());
            if (target.getId().equals(source.getId())) {
                throw new BizException("不能分派给本班自己，请选择下一班");
            }
            if (target.getId() < source.getId()) {
                throw new BizException("不能把事项回派给更早建立的班次「" + target.getShiftNo()
                        + "」，请选择下一班");
            }
            // 目标必须是进行中（requireOpenShift 已校验）
        } else {
            target = nextOpenShift(source.getId())
                    .orElseThrow(() -> new BizException("还没有建立下一班，请先由下一班负责人开班后再分派"));
        }

        if (target.getId().equals(item.getTargetShiftId())) {
            // 重复分派给同一班：幂等返回，不重复落流水
            return itemDetail(item.getId());
        }
        Long oldTarget = item.getTargetShiftId();
        item.setTargetShiftId(target.getId());
        item.setExpectedVersion(item.getExpectedVersion() + 1);
        itemRepository.save(item);
        String note = "分派给班次「" + target.getShiftNo() + "」（负责人 " + target.getLeaderName() + "）"
                + (oldTarget == null ? "" : "，原承接班次为 #" + oldTarget)
                + (trimToNull(request.getNote()) == null ? "" : "。" + request.getNote().trim());
        appendLog(item, EquestrianDict.ITEM_LOG_DISPATCH, note, operator, operator.requestId());
        bumpShiftVersion(source);
        bumpShiftVersion(target);
        return itemDetail(item.getId());
    }

    // ============================== 下一班逐项承接 ==============================

    /**
     * 下一班负责人逐项确认承接。已承接重复确认：有 requestId 时幂等返回原承接，
     * 无 requestId 时给业务错误；承接记录唯一约束兜底，绝不产生重复记录。
     */
    @Transactional
    public ItemView accept(Long itemId, ItemAcceptRequest request) {
        if (request == null) {
            throw new BizException("承接失败：请求体为空");
        }
        Operator operator = requireManager(request);
        // 幂等优先：同一 requestId 的重复点击直接取回第一次的承接
        if (operator.requestId() != null) {
            HandoverAcceptance existedAcceptance =
                    acceptanceRepository.findByRequestId(operator.requestId()).orElse(null);
            if (existedAcceptance != null) {
                return itemDetail(existedAcceptance.getItemId());
            }
            HandoverItemLog existedLog = logRepository.findByRequestId(operator.requestId()).orElse(null);
            if (existedLog != null && EquestrianDict.ITEM_LOG_ACCEPT.equals(existedLog.getAction())) {
                return itemDetail(existedLog.getItemId());
            }
        }

        HandoverItem item = lockItem(itemId);
        if (EquestrianDict.ITEM_ACCEPTED.equals(item.getStatus())) {
            throw new BizException("事项「" + item.getItemNo() + "」已由 " + item.getAcceptedBy()
                    + " 承接确认，不能重复承接；已承接事项不能再改回未处理");
        }
        assertItemVersion(item, request.getExpectedVersion());

        if (item.getTargetShiftId() == null) {
            throw new BizException("事项「" + item.getItemNo() + "」还没有分派给任何班次，无法承接");
        }
        DutyShift target = requireOpenShift(item.getTargetShiftId());
        if (!target.getLeaderName().equals(operator.name())) {
            throw new BizException("只有承接班次「" + target.getShiftNo() + "」的负责人「"
                    + target.getLeaderName() + "」才能确认承接，普通教练和别班负责人不能代为确认");
        }

        LocalDateTime now = LocalDateTime.now();
        HandoverAcceptance acceptance = new HandoverAcceptance();
        acceptance.setItemId(item.getId());
        acceptance.setShiftId(target.getId());
        acceptance.setAcceptor(operator.name());
        acceptance.setNote(trimToNull(request.getNote()));
        acceptance.setRequestId(operator.requestId());
        acceptance = acceptanceRepository.save(acceptance);

        item.setStatus(EquestrianDict.ITEM_ACCEPTED);
        item.setAcceptedAt(now);
        item.setAcceptedBy(operator.name());
        item.setAcceptedNote(trimToNull(request.getNote()));
        item.setExpectedVersion(item.getExpectedVersion() + 1);
        itemRepository.save(item);

        String logNote = "由承接班次「" + target.getShiftNo() + "」负责人逐项确认承接"
                + (acceptance.getNote() == null ? "" : "：" + acceptance.getNote());
        appendLog(item, EquestrianDict.ITEM_LOG_ACCEPT, logNote, operator, operator.requestId());
        bumpShiftVersion(target);
        DutyShift source = shiftRepository.findById(item.getShiftId()).orElse(null);
        if (source != null) {
            bumpShiftVersion(source);
        }
        return itemDetail(item.getId());
    }

    // ============================== 改交接总说明 / 关班 ==============================

    /** 本班负责人补充当班交接总说明（班次进行中才允许） */
    @Transactional
    public ShiftView updateNote(Long shiftId, ShiftNoteRequest request) {
        if (request == null) {
            throw new BizException("保存失败：请求体为空");
        }
        Operator operator = requireManager(request);
        DutyShift shift = requireOpenShift(shiftId);
        if (!shift.getLeaderName().equals(operator.name())) {
            throw new BizException("只有本班负责人「" + shift.getLeaderName() + "」才能修改交接说明");
        }
        assertShiftVersion(shift, request.getExpectedVersion());
        String note = trimToNull(request.getHandoverNote());
        if (note == null) {
            throw new BizException("交接说明不能为空");
        }
        shift.setHandoverNote(note);
        shiftRepository.save(shift);
        return detail(shift.getId());
    }

    /**
     * 关班前逐项预检：返回仍未承接的事项清单（含拦截原因）。
     * 前端据此逐项弹出延期原因输入框，阻止误关班。
     */
    @Transactional(readOnly = true)
    public List<CloseBlockerView> closeBlockers(Long shiftId) {
        DutyShift shift = requireShift(shiftId);
        return closeBlockers(shift).stream().map(this::toBlockerView).toList();
    }

    private CloseBlockerView toBlockerView(Blocker blocker) {
        HandoverItem item = blocker.item();
        boolean dispatched = item.getTargetShiftId() != null
                && !item.getTargetShiftId().equals(blocker.shiftId());
        boolean incoming = item.getTargetShiftId() != null
                && item.getTargetShiftId().equals(blocker.shiftId())
                && !item.getShiftId().equals(blocker.shiftId());
        String targetNo = item.getTargetShiftId() == null ? null
                : shiftRepository.findById(item.getTargetShiftId())
                        .map(DutyShift::getShiftNo).orElse(null);
        return new CloseBlockerView(item.getId(), item.getItemNo(), item.getTitle(),
                dispatched, incoming, targetNo, blocker.reason());
    }

    /**
     * 结束班次。仍有未承接事项时逐项拦截，必须为每个事项记录延期原因；
     * 未分派的事项在同一事务里自动派给下一班。任何一项不满足整笔回滚，
     * 不会出现「班已结但事项漏派 / 延期只写了一半」。
     */
    @Transactional
    public ShiftView close(Long shiftId, ShiftCloseRequest request) {
        if (request == null) {
            throw new BizException("结班失败：请求体为空");
        }
        Operator operator = requireManager(request);

        // 串行化班次链：与开班 / 课程变更入队互斥，关班瞬间新课程变更也不会漏
        lockSeq(operator.name());

        DutyShift shift = shiftRepository.findById(shiftId)
                .orElseThrow(() -> new BizException("值班班次不存在，编号：" + shiftId));
        if (EquestrianDict.SHIFT_CLOSED.equals(shift.getStatus())) {
            throw new BizException("班次「" + shift.getShiftNo() + "」已经结束，不能重复结束");
        }
        if (!shift.getLeaderName().equals(operator.name())) {
            throw new BizException("只有本班负责人「" + shift.getLeaderName() + "」才能结束班次");
        }
        assertShiftVersion(shift, request.getExpectedVersion());

        List<Blocker> blockers = closeBlockers(shift);
        if (blockers.isEmpty()) {
            return finishClose(shift, operator);
        }

        // 有未承接事项：逐项核对延期原因
        Map<Long, String> reasons = new HashMap<>();
        if (request.getDelays() != null) {
            for (ShiftCloseRequest.DelayItem delay : request.getDelays()) {
                if (delay != null && delay.getItemId() != null) {
                    reasons.put(delay.getItemId(), trimToNull(delay.getReason()));
                }
            }
        }
        List<String> missing = new ArrayList<>();
        DutyShift next = nextOpenShift(shift.getId()).orElse(null);
        for (Blocker blocker : blockers) {
            HandoverItem item = blocker.item();
            String reason = reasons.get(item.getId());
            if (reason == null) {
                missing.add("事项「" + item.getItemNo() + " " + item.getTitle() + "」：" + blocker.reason());
                continue;
            }
            // 需要转交的两类：本班未分派（null）/ 上一班转入本班（target=本班）。
            // 已分派给「更晚班次」的本班事项不会出现在 blockers 之外的分支里；
            // 没有下一班则整笔拒绝。
            boolean incoming = item.getTargetShiftId() != null
                    && item.getTargetShiftId().equals(shift.getId());
            boolean undispatched = item.getTargetShiftId() == null;
            if ((undispatched || incoming) && next == null) {
                throw new BizException("事项「" + item.getItemNo() + " " + item.getTitle()
                        + "」还需要继续交接，但还没有建立下一班；请先由下一班负责人开班后再结束本班");
            }
            Long oldTarget = item.getTargetShiftId();
            if (undispatched || incoming) {
                item.setTargetShiftId(next.getId());
            }
            LocalDateTime now = LocalDateTime.now();
            item.setDelayReason(reason);
            item.setDelayedAt(now);
            item.setDelayedBy(operator.name());
            item.setExpectedVersion(item.getExpectedVersion() + 1);
            itemRepository.save(item);
            boolean forwarded = undispatched || incoming;
            String delayNote = "结班时仍未承接，负责人记录延期原因：" + reason;
            if (forwarded) {
                delayNote += incoming
                        ? "；本班未逐项确认承接，事项继续顺延给班次「" + next.getShiftNo() + "」"
                        : "；事项已随交接转派给班次「" + next.getShiftNo() + "」";
            }
            appendLog(item, EquestrianDict.ITEM_LOG_DELAY, delayNote,
                    operator, operator.requestId() == null ? null
                            : operator.requestId() + "#delay" + item.getId());
            if (forwarded) {
                appendLog(item, EquestrianDict.ITEM_LOG_DISPATCH,
                        (incoming ? "未承接来项继续顺延至班次「" : "结班时随延期交接自动分派给班次「")
                                + next.getShiftNo() + "」（原承接班次 #" + oldTarget + "）",
                        operator, operator.requestId() == null ? null
                                : operator.requestId() + "#dispatch" + item.getId());
                bumpShiftVersion(next);
            }
        }
        if (!missing.isEmpty()) {
            throw new BizException("还有 " + missing.size() + " 项未承接事项需要逐项记录延期原因后才能结班："
                    + String.join("；", missing));
        }
        return finishClose(shift, operator);
    }

    // ============================== 开班快照生成 ==============================

    /**
     * 从当班课程 / 马匹状态 / 栏位安排定格交接清单。
     * 与实时课程变更共用「未承接业务唯一键」去重。
     */
    private int generateSnapshot(DutyShift shift, Operator operator) {
        int count = 0;
        LocalDate today = shift.getShiftDate();

        // 1) 当班课程：今日全部未取消场次
        for (LessonSession session : sessionRepository
                .findBySessionDateBetweenOrderBySessionDateAscStartTimeAsc(today, today)) {
            if (EquestrianDict.SESSION_CANCELED.equals(session.getStatus())) {
                continue;
            }
            HandoverItem item = buildSessionItem(shift, session, EquestrianDict.CHANGE_SCHEDULED);
            if (persistIfAbsent(item)) {
                count++;
            }
        }

        // 2) 未来 7 天：满员 / 健康受影响场次
        LocalDate until = today.plusDays(SNAPSHOT_DAYS);
        for (LessonSession session : sessionRepository
                .findBySessionDateBetweenOrderBySessionDateAscStartTimeAsc(
                        today.plusDays(1), until)) {
            if (EquestrianDict.SESSION_CANCELED.equals(session.getStatus())) {
                continue;
            }
            if (Boolean.TRUE.equals(session.getHealthAffected())) {
                HandoverItem item = buildSessionItem(shift, session, EquestrianDict.CHANGE_AFFECTED);
                if (persistIfAbsent(item)) {
                    count++;
                }
            }
            if (EquestrianDict.SESSION_FULL.equals(session.getStatus())) {
                HandoverItem item = buildSessionItem(shift, session, EquestrianDict.CHANGE_FULL);
                if (persistIfAbsent(item)) {
                    count++;
                }
            }
        }

        // 3) 马匹状态：休养中的马（各核对是否有未闭环健康事件）
        for (Horse horse : horseRepository.findAllByOrderByIdAsc()) {
            if (!EquestrianDict.HORSE_RESTING.equals(horse.getStatus())) {
                continue;
            }
            List<HealthEvent> openEvents = eventRepository
                    .findByHorseIdAndStatusNotOrderByIdAsc(horse.getId(), EquestrianDict.EVENT_CLOSED);
            if (openEvents.isEmpty()) {
                continue;
            }
            String eventNos = openEvents.stream()
                    .sorted(Comparator.comparing(HealthEvent::getId))
                    .map(HealthEvent::getEventNo).collect(java.util.stream.Collectors.joining("、"));
            String title = "马匹休养：" + horse.getName() + "（" + horse.getHorseNo() + "）有 "
                    + openEvents.size() + " 起未闭环健康事件";
            String detail = "健康事件 " + eventNos + " 未闭环；休养中禁止排课，复训须负责人复训放行。";
            HandoverItem item = newItem(shift, EquestrianDict.ITEM_TYPE_HORSE,
                    EquestrianDict.REF_HORSE_STATUS, horse.getId(), null,
                    title, detail, EquestrianDict.PRIORITY_HIGH, null, null);
            if (persistIfAbsent(item)) {
                count++;
            }
        }

        // 4) 栏位安排：维护中的栏位
        for (Stall stall : stallRepository.findAllByOrderByIdAsc()) {
            if (!EquestrianDict.STALL_MAINTENANCE.equals(stall.getStatus())) {
                continue;
            }
            String title = "栏位维护中：" + stall.getStallNo() + "（" + stall.getBarnName() + "）";
            String detail = "栏位维护中，禁止入栏；完工后需验收再恢复空闲。";
            HandoverItem item = newItem(shift, EquestrianDict.ITEM_TYPE_STALL,
                    EquestrianDict.REF_STALL_MAINT, stall.getId(), null,
                    title, detail, EquestrianDict.PRIORITY_NORMAL, null, null);
            if (persistIfAbsent(item)) {
                count++;
            }
        }
        return count;
    }

    private HandoverItem buildSessionItem(DutyShift shift, LessonSession session, String changeKind) {
        Lesson lesson = lessonRepository.findById(session.getLessonId()).orElse(null);
        Coach coach = coachRepository.findById(session.getCoachId()).orElse(null);
        String lessonName = lesson == null ? "课程已删除" : lesson.getName();
        String coachName = coach == null ? "教练已删除" : coach.getName();
        String horseText = session.getHorseId() == null ? "未指定用马"
                : horseRepository.findById(session.getHorseId())
                        .map(h -> "用马 " + h.getName() + "（" + h.getHorseNo() + "）").orElse("用马已删除");
        String kind = changeKind;
        String prefix;
        String priority;
        if (EquestrianDict.CHANGE_FULL.equals(changeKind)) {
            prefix = "已满员课程：";
            priority = EquestrianDict.PRIORITY_NORMAL;
        } else if (EquestrianDict.CHANGE_AFFECTED.equals(changeKind)) {
            prefix = "课程受健康事件影响：";
            priority = EquestrianDict.PRIORITY_HIGH;
        } else if (EquestrianDict.CHANGE_CANCELED.equals(changeKind)) {
            prefix = "取消排期：";
            priority = EquestrianDict.PRIORITY_HIGH;
        } else if (EquestrianDict.CHANGE_NEW.equals(changeKind)) {
            prefix = "新排期课程：";
            priority = EquestrianDict.PRIORITY_NORMAL;
        } else {
            prefix = "当班课程：";
            priority = EquestrianDict.PRIORITY_NORMAL;
        }
        String title = prefix + session.getSessionDate() + " " + session.getStartTime() + " "
                + lessonName + "（" + coachName + "）";
        String detail = "场次 #" + session.getId() + "，" + session.getStartTime() + "-" + session.getEndTime()
                + "，教练 " + coachName + "，" + horseText + "，状态「"
                + EquestrianDict.sessionStatusName(session.getStatus()) + "」"
                + (EquestrianDict.CHANGE_AFFECTED.equals(changeKind) ? "，已被健康事件标记受影响（排期保留未删）" : "")
                + "；已约 " + nz(session.getBookedCount()) + "/" + nz(session.getCapacity()) + "。";
        return newItem(shift, EquestrianDict.ITEM_TYPE_SESSION, EquestrianDict.REF_SESSION_CHANGE,
                session.getId(), kind, title, detail, priority, coachName, null);
    }

    /**
     * 课程变更实时挂入。NEW：若该排期已有未承接事项（快照建过），不重复建；
     * CANCELED：把已存在的 NEW / 当班课程事项更新为「取消排期」并留流水，
     * 没有则补建一条取消事项。任何变更都不改已承接事项（终态不可回退）。
     */
    private void registerSessionChange(DutyShift shift, Long sessionId, String changeKind,
            String operatorName, String requestId) {
        LessonSession session = sessionRepository.findById(sessionId).orElse(null);
        if (session == null) {
            return;
        }
        String operator = operatorName == null ? "值班台" : operatorName;
        String role = operatorName == null ? EquestrianDict.ROLE_STAFF : EquestrianDict.ROLE_MANAGER;

        List<HandoverItem> existings = itemRepository.findByShiftIdAndRefTypeAndRefId(
                shift.getId(), EquestrianDict.REF_SESSION_CHANGE, sessionId);
        HandoverItem pending = existings.stream()
                .filter(i -> EquestrianDict.ITEM_PENDING.equals(i.getStatus()))
                .findFirst().orElse(null);

        if (EquestrianDict.CHANGE_CANCELED.equals(changeKind)) {
            if (pending != null && !EquestrianDict.CHANGE_CANCELED.equals(pending.getChangeKind())) {
                String oldKind = EquestrianDict.changeKindName(pending.getChangeKind());
                pending.setChangeKind(EquestrianDict.CHANGE_CANCELED);
                pending.setTitle("取消排期：" + session.getSessionDate() + " " + session.getStartTime() + " "
                        + lessonNameOf(session));
                pending.setExpectedVersion(pending.getExpectedVersion() + 1);
                itemRepository.save(pending);
                appendLog(pending, EquestrianDict.ITEM_LOG_SUPPLEMENT,
                        "排期已取消，原交接事项（" + ("".equals(oldKind) ? "当班课程" : oldKind)
                                + "）更新为「取消排期」，请下一班关注会员改约",
                        new Operator(operator, role, null), null);
                bumpShiftVersion(shift);
                return;
            }
            if (pending != null) {
                // 已经是取消事项：重复事件幂等忽略
                return;
            }
        }
        // NEW 且已有未承接事项（快照建过）：不重复建项
        if (EquestrianDict.CHANGE_NEW.equals(changeKind) && pending != null) {
            return;
        }
        if (pending != null) {
            return;
        }
        // 未承接事项已不存在（可能此前已承接）：新变更要重新建一条
        Operator op = new Operator(operator, role, requestId);
        HandoverItem item = buildSessionItem(shift, session, changeKind);
        item.setRequestId(requestId);
        item = persistNewItem(item);
        appendLog(item, EquestrianDict.ITEM_LOG_CREATE,
                EquestrianDict.CHANGE_CANCELED.equals(changeKind)
                        ? "排期取消，自动挂入本班交接清单"
                        : "新课程排期，自动挂入本班交接清单",
                op, requestId);
        bumpShiftVersion(shift);
    }

    // ============================== 关班内部流程 ==============================

    private record Blocker(HandoverItem item, Long shiftId, String reason) {
    }

    /**
     * 关班逐项拦截：未承接事项有两类都必须列出——
     * 1) 本班产生但未承接（未分派 / 已分派未确认）；
     * 2) 上一班转入本班、本班仍未逐项确认承接的来项（多班顺延，不能在本班关掉时丢失）。
     */
    private List<Blocker> closeBlockers(DutyShift shift) {
        List<Blocker> blockers = new ArrayList<>();
        for (HandoverItem item : itemRepository.findByShiftIdOrderByIdDesc(shift.getId())) {
            if (!EquestrianDict.ITEM_PENDING.equals(item.getStatus())) {
                continue;
            }
            if (item.getTargetShiftId() == null) {
                blockers.add(new Blocker(item, shift.getId(), "尚未分派给下一班，结班时必须记录延期原因并自动转交下一班"));
            } else {
                String targetNo = shiftRepository.findById(item.getTargetShiftId())
                        .map(DutyShift::getShiftNo).orElse("#" + item.getTargetShiftId());
                blockers.add(new Blocker(item, shift.getId(),
                        "已分派给班次「" + targetNo + "」但下一班尚未逐项确认承接，结班时必须记录延期原因"));
            }
        }
        // 顺延未承接的来项：item 来自更早的班、target 正指向本班
        for (HandoverItem item : itemRepository
                .findByTargetShiftIdAndStatusOrderByIdAsc(shift.getId(), EquestrianDict.ITEM_PENDING)) {
            if (item.getShiftId().equals(shift.getId())) {
                continue; // 本班产生的已在上面
            }
            DutyShift origin = shiftRepository.findById(item.getShiftId()).orElse(null);
            String originNo = origin == null ? "#" + item.getShiftId() : origin.getShiftNo();
            blockers.add(new Blocker(item, shift.getId(),
                    "上一班「" + originNo + "」转入的事项本班仍未逐项确认承接，结班时必须记录延期原因并继续顺延"));
        }
        return blockers;
    }

    private ShiftView finishClose(DutyShift shift, Operator operator) {
        shift.setStatus(EquestrianDict.SHIFT_CLOSED);
        shift.setClosedAt(LocalDateTime.now());
        shiftRepository.save(shift);
        return detail(shift.getId());
    }

    // ============================== 视图组装 ==============================

    private ShiftView toShiftView(DutyShift shift, boolean withLogs) {
        List<HandoverItem> own = itemRepository.findByShiftIdOrderByIdDesc(shift.getId());
        List<ItemView> items = own.stream()
                .map(i -> itemView(i, withLogs, null))
                .toList();
        List<HandoverItem> incomingEntities = itemRepository
                .findByTargetShiftIdAndStatusOrderByIdAsc(shift.getId(), EquestrianDict.ITEM_PENDING);
        List<ItemView> incoming = incomingEntities.stream()
                .map(i -> itemView(i, false, shift.getId()))
                .toList();
        long pending = own.stream().filter(i -> EquestrianDict.ITEM_PENDING.equals(i.getStatus())).count();
        ShiftBriefView nextBrief = nextOpenShift(shift.getId()).map(this::toBriefView).orElse(null);
        List<CloseBlockerView> blockers = EquestrianDict.SHIFT_OPEN.equals(shift.getStatus())
                ? closeBlockers(shift).stream().map(this::toBlockerView).toList()
                : List.of();
        return new ShiftView(shift.getId(), shift.getShiftNo(), shift.getShiftDate(),
                shift.getLeaderName(), shift.getHandoverNote(), shift.getStatus(),
                EquestrianDict.shiftStatusName(shift.getStatus()), shift.getItemVersion(),
                shift.getStartedAt(), shift.getClosedAt(), items, incoming,
                pending, incoming.size(), nextBrief, blockers, shift.getCreatedAt(), shift.getUpdatedAt());
    }

    private ShiftBriefView toBriefView(DutyShift shift) {
        long pending = itemRepository.countByShiftIdAndStatus(shift.getId(), EquestrianDict.ITEM_PENDING);
        long incoming = itemRepository
                .findByTargetShiftIdAndStatusOrderByIdAsc(shift.getId(), EquestrianDict.ITEM_PENDING).size();
        return new ShiftBriefView(shift.getId(), shift.getShiftNo(), shift.getShiftDate(),
                shift.getLeaderName(), shift.getStatus(),
                EquestrianDict.shiftStatusName(shift.getStatus()),
                pending, incoming, shift.getStartedAt(), shift.getClosedAt());
    }

    /** 事项详情（完整操作流水） */
    @Transactional(readOnly = true)
    public ItemView detailItem(Long itemId) {
        HandoverItem item = requireItem(itemId);
        return itemView(item, true, null);
    }

    private ItemView itemDetail(Long itemId) {
        HandoverItem item = requireItem(itemId);
        return itemView(item, true, null);
    }

    private ItemView itemView(HandoverItem item, boolean withLogs, Long currentShiftId) {
        DutyShift source = shiftRepository.findById(item.getShiftId()).orElse(null);
        DutyShift target = item.getTargetShiftId() == null ? null
                : shiftRepository.findById(item.getTargetShiftId()).orElse(null);
        AcceptanceView acceptance = acceptanceRepository.findByItemId(item.getId())
                .map(a -> new AcceptanceView(a.getId(), a.getItemId(), a.getShiftId(),
                        shiftRepository.findById(a.getShiftId()).map(DutyShift::getShiftNo).orElse(""),
                        a.getAcceptor(), a.getNote(), a.getCreatedAt()))
                .orElse(null);
        List<ItemLogView> logs = null;
        String createdBy = null;
        String createdRole = null;
        if (withLogs) {
            List<HandoverItemLog> raw = logRepository.findByItemIdOrderByIdAsc(item.getId());
            logs = raw.stream().map(this::toLogView).toList();
            HandoverItemLog create = raw.stream()
                    .filter(l -> EquestrianDict.ITEM_LOG_CREATE.equals(l.getAction()))
                    .findFirst().orElse(null);
            if (create != null) {
                createdBy = create.getOperator();
                createdRole = create.getRole();
            }
        }
        boolean incoming = currentShiftId != null
                && currentShiftId.equals(item.getTargetShiftId())
                && EquestrianDict.ITEM_PENDING.equals(item.getStatus());
        return new ItemView(item.getId(), item.getItemNo(), item.getShiftId(),
                source == null ? "" : source.getShiftNo(),
                item.getTargetShiftId(), target == null ? null : target.getShiftNo(),
                item.getItemType(), EquestrianDict.itemTypeName(item.getItemType()),
                item.getRefType(), item.getRefId(), item.getChangeKind(),
                EquestrianDict.changeKindName(item.getChangeKind()),
                item.getTitle(), item.getDetail(), item.getPriority(),
                EquestrianDict.itemPriorityName(item.getPriority()),
                item.getOwnerName(), item.getStatus(),
                EquestrianDict.itemStatusName(item.getStatus()), item.getExpectedVersion(),
                item.getAcceptedAt(), item.getAcceptedBy(), item.getAcceptedNote(),
                item.getDelayReason(), item.getDelayedAt(), item.getDelayedBy(),
                createdBy, createdRole,
                createdRole == null ? null : EquestrianDict.roleName(createdRole),
                incoming, acceptance, logs, item.getCreatedAt(), item.getUpdatedAt());
    }

    private ItemLogView toLogView(HandoverItemLog log) {
        return new ItemLogView(log.getId(), log.getItemId(), log.getAction(),
                EquestrianDict.itemLogActionName(log.getAction()), log.getNote(), log.getOperator(),
                log.getRole(), EquestrianDict.roleName(log.getRole()), log.getCreatedAt());
    }

    private SessionHandoverView toSessionHandover(HandoverItem item) {
        DutyShift source = shiftRepository.findById(item.getShiftId()).orElse(null);
        DutyShift target = item.getTargetShiftId() == null ? null
                : shiftRepository.findById(item.getTargetShiftId()).orElse(null);
        String acceptedBy = null;
        String acceptedShiftNo = null;
        HandoverAcceptance acceptance = acceptanceRepository.findByItemId(item.getId()).orElse(null);
        if (acceptance != null) {
            acceptedBy = acceptance.getAcceptor();
            acceptedShiftNo = shiftRepository.findById(acceptance.getShiftId())
                    .map(DutyShift::getShiftNo).orElse(null);
        }
        return new SessionHandoverView(
                source == null ? null : source.getShiftNo(),
                source == null ? null : source.getShiftDate(),
                target == null ? null : target.getShiftNo(),
                item.getItemNo(), item.getStatus(),
                EquestrianDict.itemStatusName(item.getStatus()),
                EquestrianDict.changeKindName(item.getChangeKind()),
                acceptedBy, acceptedShiftNo);
    }

    // ============================== 内部小工具 ==============================

    private void lockSeq(String holder) {
        var lock = seqLockRepository.lock()
                .orElseThrow(() -> new BizException("交接序号锁未初始化（duty_seq_lock 缺少 id=1 行）"));
        lock.setHolder(holder);
        lock.setUpdatedAt(LocalDateTime.now());
        seqLockRepository.save(lock);
    }

    private Optional<DutyShift> nextOpenShift(Long sourceShiftId) {
        return shiftRepository.findAllByOrderByIdDesc().stream()
                .filter(s -> EquestrianDict.SHIFT_OPEN.equals(s.getStatus()))
                .filter(s -> s.getId() > sourceShiftId)
                .min(Comparator.comparing(DutyShift::getId));
    }

    private HandoverItem newItem(DutyShift shift, String itemType, String refType, Long refId,
            String changeKind, String title, String detail, String priority,
            String ownerName, String requestId) {
        HandoverItem item = new HandoverItem();
        item.setShiftId(shift.getId());
        item.setTargetShiftId(null);
        item.setItemType(itemType);
        item.setRefType(refType);
        item.setRefId(refId);
        item.setChangeKind(changeKind);
        item.setTitle(title);
        item.setDetail(detail);
        item.setPriority(priority);
        item.setOwnerName(ownerName);
        item.setStatus(EquestrianDict.ITEM_PENDING);
        item.setExpectedVersion(0L);
        item.setRequestId(requestId);
        item.setItemNo("HI-TMP-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        return item;
    }

    /** 落库并把占位编号改成正式 HI-00000x */
    private HandoverItem persistNewItem(HandoverItem item) {
        item = itemRepository.save(item);
        long seq = Math.max(item.getId(),
                itemRepository.findFirstByOrderByIdDesc().map(HandoverItem::getId).orElse(0L));
        item.setItemNo(String.format("HI-%06d", seq));
        return itemRepository.save(item);
    }

    /**
     * 未承接业务唯一键去重：同一班次对同一业务对象已有未承接事项则跳过。
     * 手工事项 ref 为空，天然不撞键。唯一索引 uk_item_open_dedup 在数据库兜底。
     */
    private boolean persistIfAbsent(HandoverItem item) {
        if (item.getRefType() != null && item.getRefId() != null) {
            Optional<HandoverItem> existing = itemRepository
                    .findByShiftIdAndRefTypeAndRefIdAndChangeKindAndStatus(item.getShiftId(),
                            item.getRefType(), item.getRefId(), item.getChangeKind(),
                            EquestrianDict.ITEM_PENDING);
            if (existing.isPresent()) {
                return false;
            }
        }
        persistNewItem(item);
        return true;
    }

    private void appendLog(HandoverItem item, String action, String note,
            Operator operator, String requestId) {
        HandoverItemLog log = new HandoverItemLog();
        log.setItemId(item.getId());
        log.setAction(action);
        log.setNote(truncate(note, 500));
        log.setOperator(operator.name());
        log.setRole(operator.role());
        log.setRequestId(requestId);
        logRepository.save(log);
    }

    private void bumpShiftVersion(DutyShift shift) {
        if (shift == null || shift.getId() == null) {
            return;
        }
        shift.setItemVersion((shift.getItemVersion() == null ? 0L : shift.getItemVersion()) + 1);
        shiftRepository.save(shift);
    }

    /** 事项变更后同步来源班次与承接班次的清单版本 */
    private void bumpVersionsForItemChange(HandoverItem item, DutyShift source) {
        bumpShiftVersion(source);
        if (item.getTargetShiftId() != null
                && (source == null || !item.getTargetShiftId().equals(source.getId()))) {
            shiftRepository.findById(item.getTargetShiftId()).ifPresent(this::bumpShiftVersion);
        }
    }

    private HandoverItem lockItem(Long itemId) {
        return itemRepository.findByIdForUpdate(itemId)
                .orElseThrow(() -> new BizException("交接事项不存在，编号：" + itemId));
    }

    private HandoverItem requireItem(Long itemId) {
        if (itemId == null) {
            throw new BizException("请先选择交接事项");
        }
        return itemRepository.findById(itemId)
                .orElseThrow(() -> new BizException("交接事项不存在，编号：" + itemId));
    }

    private DutyShift requireShift(Long shiftId) {
        if (shiftId == null) {
            throw new BizException("请先选择值班班次");
        }
        return shiftRepository.findById(shiftId)
                .orElseThrow(() -> new BizException("值班班次不存在，编号：" + shiftId));
    }

    private DutyShift requireOpenShift(Long shiftId) {
        DutyShift shift = requireShift(shiftId);
        if (EquestrianDict.SHIFT_CLOSED.equals(shift.getStatus())) {
            throw new BizException("班次「" + shift.getShiftNo() + "」已经结束，已结班的清单不能再修改");
        }
        return shift;
    }

    private void requireSourceLeader(DutyShift source, Operator operator) {
        if (!source.getLeaderName().equals(operator.name())) {
            throw new BizException("只有来源班次「" + source.getShiftNo() + "」的负责人「"
                    + source.getLeaderName() + "」才能操作该事项");
        }
    }

    private void requirePending(HandoverItem item, String scene) {
        if (EquestrianDict.ITEM_ACCEPTED.equals(item.getStatus())) {
            throw new BizException("事项「" + item.getItemNo() + "」已由 " + item.getAcceptedBy()
                    + " 承接，已承接事项不能再" + scene + "，也不能被旧班次改回未处理");
        }
    }

    /** 事项乐观版本：页面打开后事项被别人动过就直接 409，要求重新载入 */
    private void assertItemVersion(HandoverItem item, Long expected) {
        if (expected == null) {
            throw new BizException("缺少事项版本号，请重新载入交接清单后再提交");
        }
        long current = item.getExpectedVersion() == null ? 0L : item.getExpectedVersion();
        if (expected.longValue() != current) {
            throw new ConflictException("事项「" + item.getItemNo() + "」已被其他人更新（你看到的版本 v"
                    + expected + "，当前版本 v" + current + "），数据已变化，请重新载入交接清单后再提交，本次修改未保存");
        }
    }

    /** 班次清单乐观版本：关班 / 改总说明时清单被别人动过就 409 */
    private void assertShiftVersion(DutyShift shift, Long expected) {
        if (expected == null) {
            throw new BizException("缺少班次清单版本号，请重新载入班次后再提交");
        }
        long current = shift.getItemVersion() == null ? 0L : shift.getItemVersion();
        if (expected.longValue() != current) {
            throw new ConflictException("班次「" + shift.getShiftNo() + "」的交接清单已被其他人更新"
                    + "（你看到的版本 v" + expected + "，当前版本 v" + current
                    + "），数据已变化，请重新载入后再提交，本次操作未执行");
        }
    }

    private Operator requireManager(OperatorRequest request) {
        Operator operator = requireOperator(request);
        if (!EquestrianDict.ROLE_MANAGER.equals(operator.role())) {
            throw new BizException("只有值班负责人才能执行该操作，当前身份是普通教练");
        }
        return operator;
    }

    /** 校验并归一化操作人身份；角色合法性后端兜底，不信任前端按钮显隐 */
    private Operator requireOperator(OperatorRequest request) {
        if (request == null) {
            throw new BizException("缺少操作人信息，请先选择值班身份");
        }
        String name = trimToNull(request.getOperatorName());
        if (name == null) {
            throw new BizException("请先选择操作人（值班身份）");
        }
        String role = request.getOperatorRole();
        if (!EquestrianDict.isValidRole(role)) {
            throw new BizException("操作人角色不合法：" + role + "，只支持 STAFF / MANAGER");
        }
        return new Operator(name, role, trimToNull(request.getRequestId()));
    }

    private HandoverItemLog existingIdempotentLog(String requestId) {
        if (requestId == null) {
            return null;
        }
        return logRepository.findByRequestId(requestId).orElse(null);
    }

    private String lessonNameOf(LessonSession session) {
        return lessonRepository.findById(session.getLessonId()).map(Lesson::getName).orElse("课程已删除");
    }

    private int nz(Integer value) {
        return value == null ? 0 : value;
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max);
    }

    private String trimToNull(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record Operator(String name, String role, String requestId) {
    }
}
