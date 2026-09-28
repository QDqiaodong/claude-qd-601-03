package com.equestrian.club.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.equestrian.club.common.BizException;
import com.equestrian.club.common.ConflictException;
import com.equestrian.club.dict.EquestrianDict;
import com.equestrian.club.domain.Coach;
import com.equestrian.club.domain.CoachRepository;
import com.equestrian.club.domain.HealthEvent;
import com.equestrian.club.domain.HealthEventLog;
import com.equestrian.club.domain.HealthEventLogRepository;
import com.equestrian.club.domain.HealthEventRepository;
import com.equestrian.club.domain.HealthEventSession;
import com.equestrian.club.domain.HealthEventSessionRepository;
import com.equestrian.club.domain.HealthReview;
import com.equestrian.club.domain.HealthReviewRepository;
import com.equestrian.club.domain.Horse;
import com.equestrian.club.domain.HorseRepository;
import com.equestrian.club.domain.Lesson;
import com.equestrian.club.domain.LessonRepository;
import com.equestrian.club.domain.LessonSession;
import com.equestrian.club.domain.LessonSessionRepository;
import com.equestrian.club.dto.HealthEventCreateRequest;
import com.equestrian.club.dto.HealthReleaseRequest;
import com.equestrian.club.dto.HealthReviewRequest;
import com.equestrian.club.dto.HealthSupplementRequest;
import com.equestrian.club.dto.HealthTransitionRequest;
import com.equestrian.club.dto.OperatorRequest;
import com.equestrian.club.dto.view.AffectedSessionView;
import com.equestrian.club.dto.view.HealthEventView;
import com.equestrian.club.dto.view.HealthLogView;
import com.equestrian.club.dto.view.HealthReviewView;
import com.equestrian.club.dto.view.HorseHealthView;

/**
 * 马匹健康事件闭环服务：登记 -> 待处理 / 观察中 / 待复查 -> 复查 -> 负责人复训放行 -> 关闭。
 *
 * <p>一致性由三层共同保证：
 * <ol>
 *   <li>每个写事务先对 horse 行上悲观锁（{@code findByIdForUpdate}），把版本比对、
 *       事件增改、马匹状态联动收成一条串行临界区；</li>
 *   <li>请求回传马匹健康事件链版本 {@code healthVersion}，与库里不一致直接 409，
 *       别人期间登记的新伤病 / 新复查不会被旧页面覆盖；</li>
 *   <li>每次点击带 requestId，日志表唯一约束兜底，重复点击不会产生两条记录、
 *       也不会重复改变马匹状态。</li>
 * </ol>
 */
@Service
public class HealthEventService {

    private final HealthEventRepository eventRepository;
    private final HealthEventLogRepository logRepository;
    private final HealthReviewRepository reviewRepository;
    private final HealthEventSessionRepository eventSessionRepository;
    private final HorseRepository horseRepository;
    private final LessonSessionRepository sessionRepository;
    private final LessonRepository lessonRepository;
    private final CoachRepository coachRepository;

    public HealthEventService(HealthEventRepository eventRepository,
            HealthEventLogRepository logRepository,
            HealthReviewRepository reviewRepository,
            HealthEventSessionRepository eventSessionRepository,
            HorseRepository horseRepository,
            LessonSessionRepository sessionRepository,
            LessonRepository lessonRepository,
            CoachRepository coachRepository) {
        this.eventRepository = eventRepository;
        this.logRepository = logRepository;
        this.reviewRepository = reviewRepository;
        this.eventSessionRepository = eventSessionRepository;
        this.horseRepository = horseRepository;
        this.sessionRepository = sessionRepository;
        this.lessonRepository = lessonRepository;
        this.coachRepository = coachRepository;
    }

    // ============================== 查询 ==============================

    /**
     * 处置台列表：可按马匹、状态、是否逾期组合筛选。
     */
    @Transactional(readOnly = true)
    public List<HealthEventView> list(Long horseId, String status, Boolean overdue) {
        List<HealthEvent> events;
        if (horseId != null && status != null && !status.isBlank()) {
            events = eventRepository.findByHorseIdAndStatusOrderByIdDesc(horseId, status);
        } else if (horseId != null) {
            events = eventRepository.findByHorseIdOrderByIdDesc(horseId);
        } else if (status != null && !status.isBlank()) {
            events = eventRepository.findByStatusOrderByIdDesc(status);
        } else {
            events = eventRepository.findAllByOrderByIdDesc();
        }
        LocalDate today = LocalDate.now();
        return events.stream()
                .filter(event -> overdue == null || overdue.booleanValue() == isOverdue(event, today))
                .map(event -> toView(event, false, today))
                .toList();
    }

    /** 事件详情：完整事件链（复查历史 + 处置历史 + 受影响排期） */
    @Transactional(readOnly = true)
    public HealthEventView detail(Long id) {
        return toView(requireEvent(id), true, LocalDate.now());
    }

    /** 马匹详情一屏看风险：未闭环事件 + 最近复查结论 + 放行条件预检 */
    @Transactional(readOnly = true)
    public HorseHealthView horseHealth(Long horseId) {
        Horse horse = requireHorse(horseId);
        LocalDate today = LocalDate.now();

        List<HealthEvent> openEvents =
                eventRepository.findByHorseIdAndStatusNotOrderByIdAsc(horseId, EquestrianDict.EVENT_CLOSED);
        List<HealthEventView> openViews = new ArrayList<>();
        for (HealthEvent event : openEvents) {
            openViews.add(toView(event, false, today));
        }
        openViews.sort(Comparator.comparing(HealthEventView::id).reversed());

        HealthReview latest = reviewRepository.findByHorseIdOrderByIdAsc(horseId).stream()
                .reduce((first, second) -> second)
                .orElse(null);

        List<String> blockers = releaseBlockers(horse, openEvents, today);
        int overdueCount = (int) openViews.stream().filter(HealthEventView::overdue).count();

        return new HorseHealthView(
                horse.getId(),
                horse.getHorseNo(),
                horse.getName(),
                horse.getStatus(),
                EquestrianDict.horseStatusName(horse.getStatus()),
                horse.getHealthVersion(),
                openViews,
                latest == null ? null : toReviewView(latest, today),
                blockers.isEmpty(),
                blockers,
                openEvents.size(),
                overdueCount);
    }

    // ============================== 登记 ==============================

    /**
     * 登记健康事件。
     * 高风险（HIGH）：马匹立即进入休养；其当时名下的未来未取消场次逐场标为受影响并挂到事件上，
     * 场次不删除。中 / 低风险只建事件，不改变马匹在役状态。
     */
    @Transactional
    public HealthEventView create(HealthEventCreateRequest request) {
        if (request == null || request.getHorseId() == null) {
            throw new BizException("登记失败：请选择马匹");
        }
        Operator operator = requireOperator(request);

        // 幂等：重复点击直接取回第一次登记的事件，绝不产生第二条
        if (operator.requestId != null) {
            HealthEventLog existed = logRepository.findByRequestId(operator.requestId).orElse(null);
            if (existed != null && EquestrianDict.ACTION_REGISTER.equals(existed.getAction())) {
                return detail(existed.getEventId());
            }
        }

        LocalDateTime occurredAt = request.getOccurredAt() == null ? LocalDateTime.now() : request.getOccurredAt();
        if (occurredAt.isAfter(LocalDateTime.now().plusMinutes(1))) {
            throw new BizException("发生时间不能晚于当前时间");
        }
        String severity = request.getSeverity();
        if (!EquestrianDict.isValidSeverity(severity)) {
            throw new BizException("严重程度取值不合法：" + severity + "，只支持 HIGH / MEDIUM / LOW");
        }
        String symptom = trimToNull(request.getSymptom());
        if (symptom == null) {
            throw new BizException("请填写症状说明");
        }
        String advice = trimToNull(request.getTreatmentAdvice());
        if (advice == null) {
            throw new BizException("请填写处置建议");
        }
        if (request.getExpectedReviewDate() == null) {
            throw new BizException("请填写预计复查日");
        }

        // 锁马：版本号与马匹状态联动都在临界区内完成
        Horse horse = lockHorse(request.getHorseId());
        // 登记允许从列表页直接发起，不强制带版本；真正的防覆盖靠行锁 + 登记后的最新版本号
        assertVersion(horse, null, true);

        HealthEvent event = new HealthEvent();
        event.setHorseId(horse.getId());
        event.setOccurredAt(occurredAt);
        event.setSeverity(severity);
        event.setSymptom(symptom);
        event.setTreatmentAdvice(advice);
        event.setExpectedReviewDate(request.getExpectedReviewDate());
        event.setStatus(EquestrianDict.EVENT_PENDING);
        event.setCreatedBy(operator.name);
        event.setCreatedRole(operator.role);
        // event_no 非空且唯一：自增 id 要在落库后才知道，先放一个唯一占位号，
        // 拿到 id 后立即改成正式编号 HE-00000x（同事务内，外部读到的永远是正式编号）
        event.setEventNo("HE-TMP-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        event = eventRepository.save(event);
        // 编号以自增 id 为准（MySQL 在显式插入种子低位 id 后会把自增计数器推到 max+1；
        // 个别库（如演示用的 H2）不会，这里取 max 兜底，保证编号唯一且单调）
        long seq = Math.max(event.getId(),
                eventRepository.findFirstByOrderByIdDesc().map(HealthEvent::getId).orElse(0L));
        event.setEventNo(String.format("HE-%06d", seq));
        event = eventRepository.save(event);

        appendLog(event, EquestrianDict.ACTION_REGISTER, null, EquestrianDict.EVENT_PENDING,
                "登记健康事件（" + EquestrianDict.severityName(severity) + "）：" + symptom,
                operator, operator.requestId);

        // 高风险：立即休养（在役才联动；已休养保持休养；退役为终态不回改，只登记事件并提示）
        boolean high = EquestrianDict.isHighSeverity(severity);
        if (high && EquestrianDict.HORSE_ACTIVE.equals(horse.getStatus())) {
            horse.setStatus(EquestrianDict.HORSE_RESTING);
        }

        // 高风险：名下未来（含今天）未取消场次逐场标记受影响并关联事件，绝不删除
        if (high) {
            List<LessonSession> futureSessions = sessionRepository
                    .findByHorseIdAndSessionDateGreaterThanEqualAndStatusNotOrderBySessionDateAscStartTimeAsc(
                            horse.getId(), LocalDate.now(), EquestrianDict.SESSION_CANCELED);
            for (LessonSession session : futureSessions) {
                session.setHealthAffected(true);
                sessionRepository.save(session);
                if (!eventSessionRepository.existsByEventIdAndSessionId(event.getId(), session.getId())) {
                    HealthEventSession link = new HealthEventSession();
                    link.setEventId(event.getId());
                    link.setHorseId(horse.getId());
                    link.setSessionId(session.getId());
                    link.setMarkedBy(operator.name);
                    eventSessionRepository.save(link);
                }
            }
        }

        bumpVersion(horse);
        // 登记落定版本：该事件成为「最新事件」的基准版本，之后复查必须基于不早于它的事件链
        event.setRegisteredVersion(horse.getHealthVersion());
        eventRepository.save(event);
        return detail(event.getId());
    }

    // ============================== 补充处置 ==============================

    /** 补充处置：只追加日志，不覆盖任何已有字段与历史 */
    @Transactional
    public HealthEventView supplement(Long eventId, HealthSupplementRequest request) {
        if (request == null) {
            throw new BizException("补充失败：请求体为空");
        }
        Operator operator = requireOperator(request);
        String note = trimToNull(request.getNote());
        if (note == null) {
            throw new BizException("请填写要补充的处置说明");
        }

        HealthEventLog existed = existingIdempotentLog(operator.requestId);
        if (existed != null) {
            return detail(existed.getEventId());
        }

        HealthEvent event = requireEvent(eventId);
        Horse horse = lockHorse(event.getHorseId());
        assertVersion(horse, request.getExpectedVersion(), false);
        requireOpen(event, "补充处置");

        appendLog(event, EquestrianDict.ACTION_SUPPLEMENT, event.getStatus(), event.getStatus(),
                note, operator, operator.requestId);
        bumpVersion(horse);
        return detail(event.getId());
    }

    // ============================== 状态流转 ==============================

    /**
     * 人工流转：待处理 / 观察中 / 待复查 之间沿状态机边走。
     * 已关闭是终态；待复查 -> 已关闭不在这里开放（只能走负责人放行）。
     */
    @Transactional
    public HealthEventView transition(Long eventId, HealthTransitionRequest request) {
        if (request == null) {
            throw new BizException("流转失败：请求体为空");
        }
        Operator operator = requireOperator(request);
        String target = request.getTargetStatus();
        String note = trimToNull(request.getNote());
        if (note == null) {
            throw new BizException("请填写本次流转说明");
        }
        if (!EquestrianDict.isValidEventStatus(target)) {
            throw new BizException("事件状态取值不合法：" + target);
        }

        HealthEventLog existed = existingIdempotentLog(operator.requestId);
        if (existed != null) {
            return detail(existed.getEventId());
        }

        HealthEvent event = requireEvent(eventId);
        Horse horse = lockHorse(event.getHorseId());
        assertVersion(horse, request.getExpectedVersion(), false);
        requireOpen(event, "流转");

        String current = event.getStatus();
        if (current.equals(target)) {
            throw new BizException("事件「" + event.getEventNo() + "」已经是「"
                    + EquestrianDict.eventStatusName(current) + "」状态，无需重复流转");
        }
        if (!EquestrianDict.canTransferEventStatus(current, target)) {
            throw new BizException("健康事件不能从「" + EquestrianDict.eventStatusName(current)
                    + "」直接流转为「" + EquestrianDict.eventStatusName(target)
                    + "」；已关闭只能由负责人复训放行");
        }

        event.setStatus(target);
        // 进入待复查而没有预计复查日时，兜底要求前端已填（预计复查日为事件必填字段，这里保持非空）
        eventRepository.save(event);
        appendLog(event, EquestrianDict.ACTION_TRANSITION, current, target, note, operator, operator.requestId);
        bumpVersion(horse);
        return detail(event.getId());
    }

    // ============================== 复查 ==============================

    /**
     * 复查（只有待复查事件可复查）：
     * 继续观察 -> 事件退回观察中（必须给下次复查日）；
     * 调整下次复查日 -> 留在待复查（必须给下次复查日）；
     * 复查合格申请放行 -> 留在待复查，挂合格结论等负责人确认（下次复查日可空=长期有效）。
     */
    @Transactional
    public HealthEventView review(Long eventId, HealthReviewRequest request) {
        if (request == null) {
            throw new BizException("复查失败：请求体为空");
        }
        Operator operator = requireOperator(request);
        String result = request.getResult();
        if (!EquestrianDict.isValidReviewResult(result)) {
            throw new BizException("复查结论取值不合法：" + result
                    + "，只支持 OBSERVE 继续观察 / RESCHEDULE 调整下次复查日 / PASS 复查合格");
        }
        String conclusion = trimToNull(request.getConclusion());
        if (conclusion == null) {
            throw new BizException("请填写复查结论");
        }
        boolean pass = EquestrianDict.REVIEW_PASS.equals(result);
        if (!pass && request.getNextReviewDate() == null) {
            throw new BizException(EquestrianDict.reviewResultName(result) + "必须填写下次复查日");
        }

        // 一次复查会落「一条复查 + 一条日志」：复查记录用 requestId 幂等，
        // 日志用 requestId#log 幂等（两张表各自有 requestId 唯一约束）
        HealthReview idempotent = operator.requestId == null ? null
                : reviewRepository.findByRequestId(operator.requestId).orElse(null);
        if (idempotent != null) {
            return detail(idempotent.getEventId());
        }

        HealthEvent event = requireEvent(eventId);
        Horse horse = lockHorse(event.getHorseId());
        assertVersion(horse, request.getExpectedVersion(), false);
        if (!EquestrianDict.EVENT_REVIEW_PENDING.equals(event.getStatus())) {
            throw new BizException("事件「" + event.getEventNo() + "」当前为「"
                    + EquestrianDict.eventStatusName(event.getStatus())
                    + "」，只有「待复查」事件可以提交复查结论");
        }

        LocalDate reviewDate = request.getReviewDate() == null ? LocalDate.now() : request.getReviewDate();
        String fromStatus = event.getStatus();
        String toStatus = fromStatus;
        String logNote;

        HealthReview review = new HealthReview();
        review.setEventId(event.getId());
        review.setHorseId(horse.getId());
        review.setReviewDate(reviewDate);
        review.setResult(result);
        review.setConclusion(conclusion);
        review.setNextReviewDate(request.getNextReviewDate());
        // 复查依据：落库前的最新版本（本次复查 bump 后，horse 版本会 +1）
        review.setBasedVersion(horse.getHealthVersion());
        review.setReviewer(operator.name);
        review.setRole(operator.role);
        review.setRequestId(operator.requestId);
        review = reviewRepository.save(review);

        event.setLatestReviewId(review.getId());

        if (EquestrianDict.REVIEW_OBSERVE.equals(result)) {
            event.setStatus(EquestrianDict.EVENT_OBSERVING);
            event.setExpectedReviewDate(request.getNextReviewDate());
            toStatus = EquestrianDict.EVENT_OBSERVING;
            logNote = "复查结论：继续观察。" + conclusion;
        } else if (EquestrianDict.REVIEW_RESCHEDULE.equals(result)) {
            event.setExpectedReviewDate(request.getNextReviewDate());
            logNote = "复查结论：调整下次复查日为 " + request.getNextReviewDate() + "。" + conclusion;
        } else {
            if (request.getNextReviewDate() != null) {
                // 合格但有有效期：到期日前必须复训 / 放行
                event.setExpectedReviewDate(request.getNextReviewDate());
            } else {
                // 长期合格：清掉原来的预计复查日，避免列表把「已待放行」误报为逾期
                event.setExpectedReviewDate(null);
            }
            logNote = "复查结论：复查合格，申请复训放行，等待负责人确认。" + conclusion;
        }

        eventRepository.save(event);
        appendLog(event, EquestrianDict.ACTION_REVIEW, fromStatus, toStatus, logNote,
                operator, operator.requestId == null ? null : operator.requestId + "#log");
        bumpVersion(horse);
        return detail(event.getId());
    }

    // ============================== 复训放行（负责人） ==============================

    /**
     * 负责人确认复训放行。全部满足才把马恢复在役并关闭所有未闭环事件：
     * <ol>
     *   <li>操作人角色必须是 MANAGER（前端隐藏按钮不算数，这里强制）；</li>
     *   <li>马匹处于休养（在役无需放行，退役是终态不能恢复）；</li>
     *   <li>每个未关闭事件都有复查，且最后一条复查是「合格」；</li>
     *   <li>合格结论未过期（PASS 带了有效期的，过了那天即失效）；</li>
     *   <li>合格结论必须基于最新事件链：该事件之后没有更晚登记的新事件。</li>
     * </ol>
     * 任何一条不满足都拒绝并逐条说明原因。
     */
    @Transactional
    public HorseHealthView release(Long horseId, HealthReleaseRequest request) {
        if (request == null) {
            throw new BizException("放行失败：请求体为空");
        }
        Operator operator = requireOperator(request);
        if (!EquestrianDict.ROLE_MANAGER.equals(operator.role)) {
            throw new BizException("复训放行只能由负责人确认，当前操作人是普通工作人员，无权放行");
        }

        // 放行幂等：同一 requestId 的重复点击只生效一次（以放行日志为准）
        HealthEventLog existed = existingIdempotentLog(operator.requestId);
        Horse horse = lockHorse(horseId);
        if (existed != null && EquestrianDict.ACTION_RELEASE.equals(existed.getAction())) {
            return horseHealth(horseId);
        }

        assertVersion(horse, request.getExpectedVersion(), false);

        List<HealthEvent> openEvents =
                eventRepository.findByHorseIdAndStatusNotOrderByIdAsc(horseId, EquestrianDict.EVENT_CLOSED);
        List<String> blockers = releaseBlockers(horse, openEvents, LocalDate.now());
        if (!blockers.isEmpty()) {
            throw new BizException("复训放行被拒绝：" + String.join("；", blockers));
        }

        // 全部通过：关闭所有未闭环事件、逐事件留放行日志、马匹恢复在役
        LocalDateTime now = LocalDateTime.now();
        String note = trimToNull(request.getNote());
        String baseNote = "负责人确认复训放行，全部未闭环事件复查合格" + (note == null ? "" : "。" + note);
        int index = 0;
        for (HealthEvent event : openEvents) {
            String from = event.getStatus();
            event.setStatus(EquestrianDict.EVENT_CLOSED);
            event.setClosedAt(now);
            event.setClosedBy(operator.name);
            eventRepository.save(event);
            // 同一事务里多个事件多条放行日志：第一条占用 requestId，其余加后缀，既保留追溯又不撞唯一键
            appendLog(event, EquestrianDict.ACTION_RELEASE, from, EquestrianDict.EVENT_CLOSED,
                    baseNote, operator, index == 0 ? operator.requestId : operator.requestId + "#" + event.getId());
            index++;
        }

        horse.setStatus(EquestrianDict.HORSE_ACTIVE);
        bumpVersion(horse);
        return horseHealth(horseId);
    }

    // ============================== 规则判定 ==============================

    /**
     * 放行条件预检（放行接口与马匹详情页共用，拒绝原因前后端展示一致）。
     * 返回空列表 = 可以放行。
     */
    private List<String> releaseBlockers(Horse horse, List<HealthEvent> openEvents, LocalDate today) {
        List<String> blockers = new ArrayList<>();
        String name = "马匹「" + horse.getName() + "」";

        if (EquestrianDict.HORSE_RETIRED.equals(horse.getStatus())) {
            blockers.add(name + "已退役，退役为终态，不能恢复在役");
        }
        if (EquestrianDict.HORSE_ACTIVE.equals(horse.getStatus())) {
            blockers.add(name + "当前已在役，无需复训放行");
        }
        if (openEvents.isEmpty()) {
            // 休养但没有未关闭事件：说明休养不是健康事件驱动的，走马匹档案的状态流转即可
            if (EquestrianDict.HORSE_RESTING.equals(horse.getStatus())) {
                blockers.add(name + "没有未关闭的健康事件，不能用健康事件通道放行，请在马匹档案中流转状态");
            }
            return blockers;
        }

        // 最新伤病事件的登记版本：任何合格复查的 basedVersion 都不能落后于它
        long newestEventVersion = openEvents.stream()
                .mapToLong(e -> e.getRegisteredVersion() == null ? 0L : e.getRegisteredVersion())
                .max().orElse(0L);

        for (HealthEvent event : openEvents) {
            String label = "事件「" + event.getEventNo() + "」";
            HealthReview latest = event.getLatestReviewId() == null ? null
                    : reviewRepository.findById(event.getLatestReviewId()).orElse(null);
            if (latest == null) {
                latest = reviewRepository.findFirstByEventIdOrderByIdDesc(event.getId()).orElse(null);
            }
            if (latest == null) {
                blockers.add(label + "还没有任何复查记录，需先完成合格复查");
                continue;
            }
            if (!EquestrianDict.REVIEW_PASS.equals(latest.getResult())) {
                blockers.add(label + "最近一次复查结论为「"
                        + EquestrianDict.reviewResultName(latest.getResult()) + "」，不是复查合格");
                continue;
            }
            if (latest.getNextReviewDate() != null && latest.getNextReviewDate().isBefore(today)) {
                blockers.add(label + "的合格复查已过期（有效至 " + latest.getNextReviewDate() + "），请重新复查");
            }
            // 当前结论基于旧事件链：复查写入时的事件链版本早于最新伤病事件的登记版本
            long based = latest.getBasedVersion() == null ? 0L : latest.getBasedVersion();
            if (based < newestEventVersion) {
                blockers.add(label + "的合格复查之后又登记了更新的伤病事件（复查基于 v" + based
                        + "，最新事件登记于 v" + newestEventVersion + "），不能依据旧结论放行，请重新复查");
            }
        }
        return blockers;
    }

    private boolean isOverdue(HealthEvent event, LocalDate today) {
        return EquestrianDict.isOpenEvent(event.getStatus())
                && event.getExpectedReviewDate() != null
                && event.getExpectedReviewDate().isBefore(today);
    }

    // ============================== 视图组装 ==============================

    private HealthEventView toView(HealthEvent event, boolean withChain, LocalDate today) {
        Horse horse = horseRepository.findById(event.getHorseId()).orElse(null);
        HealthReview latest = event.getLatestReviewId() == null ? null
                : reviewRepository.findById(event.getLatestReviewId()).orElse(null);
        if (latest == null) {
            latest = reviewRepository.findFirstByEventIdOrderByIdDesc(event.getId()).orElse(null);
        }

        List<HealthReviewView> reviews = List.of();
        List<HealthLogView> logs = List.of();
        List<AffectedSessionView> affected = List.of();
        if (withChain) {
            reviews = reviewRepository.findByEventIdOrderByIdAsc(event.getId()).stream()
                    .map(review -> toReviewView(review, today)).toList();
            logs = logRepository.findByEventIdOrderByIdAsc(event.getId()).stream()
                    .map(this::toLogView).toList();
            affected = eventSessionRepository.findByEventIdOrderByIdAsc(event.getId()).stream()
                    .map(this::toAffectedView).toList();
        }

        return new HealthEventView(
                event.getId(),
                event.getEventNo(),
                event.getHorseId(),
                horse == null ? "" : horse.getHorseNo(),
                horse == null ? "马匹已删除" : horse.getName(),
                horse == null ? "" : horse.getStatus(),
                horse == null ? "" : EquestrianDict.horseStatusName(horse.getStatus()),
                event.getOccurredAt(),
                event.getSeverity(),
                EquestrianDict.severityName(event.getSeverity()),
                EquestrianDict.isHighSeverity(event.getSeverity()),
                event.getSymptom(),
                event.getTreatmentAdvice(),
                event.getExpectedReviewDate(),
                event.getStatus(),
                EquestrianDict.eventStatusName(event.getStatus()),
                event.getCreatedBy(),
                event.getCreatedRole(),
                event.getClosedAt(),
                event.getClosedBy(),
                isOverdue(event, today),
                latest == null ? null : toReviewView(latest, today),
                horse == null ? null : horse.getHealthVersion(),
                reviews,
                logs,
                affected,
                event.getCreatedAt(),
                event.getUpdatedAt());
    }

    private HealthReviewView toReviewView(HealthReview review, LocalDate today) {
        boolean expired = review.getNextReviewDate() != null
                && review.getNextReviewDate().isBefore(today);
        return new HealthReviewView(
                review.getId(),
                review.getEventId(),
                review.getReviewDate(),
                review.getResult(),
                EquestrianDict.reviewResultName(review.getResult()),
                review.getConclusion(),
                review.getNextReviewDate(),
                review.getReviewer(),
                review.getRole(),
                EquestrianDict.roleName(review.getRole()),
                expired,
                review.getCreatedAt());
    }

    private HealthLogView toLogView(HealthEventLog log) {
        return new HealthLogView(
                log.getId(),
                log.getEventId(),
                log.getAction(),
                EquestrianDict.actionName(log.getAction()),
                log.getFromStatus(),
                EquestrianDict.eventStatusName(log.getFromStatus()),
                log.getToStatus(),
                EquestrianDict.eventStatusName(log.getToStatus()),
                log.getNote(),
                log.getOperator(),
                log.getRole(),
                EquestrianDict.roleName(log.getRole()),
                log.getCreatedAt());
    }

    private AffectedSessionView toAffectedView(HealthEventSession link) {
        LessonSession session = sessionRepository.findById(link.getSessionId()).orElse(null);
        if (session == null) {
            return new AffectedSessionView(link.getSessionId(), null, "", "", "排期已删除",
                    "", "", "", 0, link.getMarkedAt(), link.getMarkedBy());
        }
        Lesson lesson = lessonRepository.findById(session.getLessonId()).orElse(null);
        Coach coach = coachRepository.findById(session.getCoachId()).orElse(null);
        return new AffectedSessionView(
                session.getId(),
                session.getSessionDate(),
                session.getStartTime(),
                session.getEndTime(),
                lesson == null ? "课程已删除" : lesson.getName(),
                coach == null ? "教练已删除" : coach.getName(),
                session.getStatus(),
                EquestrianDict.sessionStatusName(session.getStatus()),
                session.getBookedCount(),
                link.getMarkedAt(),
                link.getMarkedBy());
    }

    // ============================== 内部小工具 ==============================

    private void appendLog(HealthEvent event, String action, String from, String to,
            String note, Operator operator, String requestId) {
        HealthEventLog log = new HealthEventLog();
        log.setEventId(event.getId());
        log.setHorseId(event.getHorseId());
        log.setAction(action);
        log.setFromStatus(from);
        log.setToStatus(to);
        log.setNote(note);
        log.setOperator(operator.name);
        log.setRole(operator.role);
        log.setRequestId(requestId);
        logRepository.save(log);
    }

    private void bumpVersion(Horse horse) {
        horse.setHealthVersion((horse.getHealthVersion() == null ? 0L : horse.getHealthVersion()) + 1);
        horseRepository.save(horse);
    }

    private Horse lockHorse(Long horseId) {
        return horseRepository.findByIdForUpdate(horseId)
                .orElseThrow(() -> new BizException("马匹不存在，编号：" + horseId));
    }

    private Horse requireHorse(Long horseId) {
        if (horseId == null) {
            throw new BizException("请先选择马匹");
        }
        return horseRepository.findById(horseId)
                .orElseThrow(() -> new BizException("马匹不存在，编号：" + horseId));
    }

    private HealthEvent requireEvent(Long eventId) {
        if (eventId == null) {
            throw new BizException("请先选择健康事件");
        }
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new BizException("健康事件不存在，编号：" + eventId));
    }

    private void requireOpen(HealthEvent event, String scene) {
        if (!EquestrianDict.isOpenEvent(event.getStatus())) {
            throw new BizException("事件「" + event.getEventNo() + "」已关闭，不能再" + scene
                    + "；已发生的处置历史不允许改动");
        }
    }

    /**
     * 乐观版本校验：页面打开后这匹马的事件链一旦被别人动过（版本对不上），
     * 直接 409，让前端提示「数据已变化」并重新载入，而不是用旧数据覆盖。
     * 登记接口允许不传版本（新页面可能只拿了马匹列表）。
     */
    private void assertVersion(Horse horse, Long expected, boolean allowNull) {
        if (expected == null) {
            if (allowNull) {
                return;
            }
            throw new BizException("缺少数据版本号，请重新打开事件后再操作");
        }
        long current = horse.getHealthVersion() == null ? 0L : horse.getHealthVersion();
        if (expected.longValue() != current) {
            throw new ConflictException("这匹马的健康事件链已被其他人更新（你看到的版本 v" + expected
                    + "，当前版本 v" + current + "），请重新载入最新事件链后再提交，旧结论未被保存");
        }
    }

    private HealthEventLog existingIdempotentLog(String requestId) {
        if (requestId == null) {
            return null;
        }
        return logRepository.findByRequestId(requestId).orElse(null);
    }

    /** 校验并归一化操作人身份；角色合法性后端兜底，不信任前端按钮显隐 */
    private Operator requireOperator(OperatorRequest request) {
        String name = trimToNull(request.getOperatorName());
        if (name == null) {
            throw new BizException("请先选择操作人（值班人员身份）");
        }
        String role = request.getOperatorRole();
        if (!EquestrianDict.isValidRole(role)) {
            throw new BizException("操作人角色不合法：" + role + "，只支持 STAFF / MANAGER");
        }
        return new Operator(name, role, trimToNull(request.getRequestId()));
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
