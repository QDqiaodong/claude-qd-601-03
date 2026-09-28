package com.equestrian.club.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

import com.equestrian.club.common.BizException;
import com.equestrian.club.dict.EquestrianDict;
import com.equestrian.club.domain.Coach;
import com.equestrian.club.domain.CoachRepository;
import com.equestrian.club.domain.Horse;
import com.equestrian.club.domain.Lesson;
import com.equestrian.club.domain.LessonRepository;
import com.equestrian.club.domain.LessonSession;
import com.equestrian.club.domain.LessonSessionRepository;
import com.equestrian.club.dto.SessionRequest;
import com.equestrian.club.dto.view.CoachView;
import com.equestrian.club.dto.view.LessonView;
import com.equestrian.club.dto.view.SessionView;
import com.equestrian.club.event.SessionChangedEvent;

/**
 * 骑乘课程与排期模块：课程容量 / 同一教练同一时段不重叠 / 退役或休养的马匹禁止排课。
 *
 * <p>排期新建 / 取消成功后发布 {@link SessionChangedEvent}，交接链路据此把「新出现的
 * 课程变更」挂入当前最新 OPEN 班次，保证变更进对班次。
 */
@Service
public class SessionService {

    private final LessonRepository lessonRepository;
    private final CoachRepository coachRepository;
    private final LessonSessionRepository sessionRepository;
    private final HorseService horseService;
    private final ApplicationEventPublisher eventPublisher;
    private final HandoverService handoverService;

    public SessionService(LessonRepository lessonRepository,
            CoachRepository coachRepository,
            LessonSessionRepository sessionRepository,
            HorseService horseService,
            ApplicationEventPublisher eventPublisher,
            HandoverService handoverService) {
        this.lessonRepository = lessonRepository;
        this.coachRepository = coachRepository;
        this.sessionRepository = sessionRepository;
        this.horseService = horseService;
        this.eventPublisher = eventPublisher;
        this.handoverService = handoverService;
    }

    // ---------------- 课程 / 教练字典查询 ----------------

    @Transactional(readOnly = true)
    public List<LessonView> listLessons() {
        return lessonRepository.findAllByOrderByIdAsc().stream().map(this::toLessonView).toList();
    }

    @Transactional(readOnly = true)
    public List<CoachView> listCoaches() {
        return coachRepository.findAllByOrderByIdAsc().stream().map(this::toCoachView).toList();
    }

    // ---------------- 排期 ----------------

    @Transactional(readOnly = true)
    public List<SessionView> list() {
        return sessionRepository.findAllByOrderBySessionDateAscStartTimeAscIdAsc()
                .stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public SessionView detail(Long id) {
        return toView(require(id));
    }

    /** 供骑乘记录模块复用的取排期方法：可空外键先判 null 再查库 */
    @Transactional(readOnly = true)
    public LessonSession require(Long sessionId) {
        if (sessionId == null) {
            throw new BizException("请先选择课程排期");
        }
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BizException("排期不存在，编号：" + sessionId
                        + "；骑乘记录必须关联一条已排期的课程"));
    }

    @Transactional
    public SessionView create(SessionRequest request) {
        if (request == null || request.getLessonId() == null) {
            throw new BizException("排期失败：请选择课程");
        }
        if (request.getCoachId() == null) {
            throw new BizException("排期失败：请选择教练");
        }
        if (request.getSessionDate() == null) {
            throw new BizException("排期失败：请选择排期日期");
        }
        String startTime = request.getStartTime();
        if (startTime == null || startTime.isBlank()) {
            throw new BizException("排期失败：请填写开始时间");
        }
        if (!EquestrianDict.isValidTimeSlot(startTime)) {
            throw new BizException("开始时间「" + startTime + "」不在可用时段内，可选："
                    + String.join(" / ", EquestrianDict.TIME_SLOTS));
        }
        String endTime = request.getEndTime();
        if (endTime == null || endTime.isBlank()) {
            endTime = EquestrianDict.endOfSlot(startTime);
        }
        if (endTime == null || endTime.compareTo(startTime) <= 0) {
            throw new BizException("结束时间必须晚于开始时间");
        }

        Lesson lesson = lessonRepository.findById(request.getLessonId())
                .orElseThrow(() -> new BizException("课程不存在，编号：" + request.getLessonId()));
        if (!EquestrianDict.LESSON_ON.equals(lesson.getStatus())) {
            throw new BizException("课程「" + lesson.getName() + "」已下架，不能排期");
        }

        Coach coach = coachRepository.findById(request.getCoachId())
                .orElseThrow(() -> new BizException("教练不存在，编号：" + request.getCoachId()));
        if (!EquestrianDict.COACH_ON_DUTY.equals(coach.getStatus())) {
            throw new BizException("教练「" + coach.getName() + "」当前处于「"
                    + EquestrianDict.coachStatusName(coach.getStatus()) + "」状态，不能排期");
        }

        // 可空外键：只有真的传了马匹才去查库，并且退役 / 休养的马不能排课
        if (request.getHorseId() != null) {
            Horse horse = horseService.require(request.getHorseId());
            horseService.requireRideable(horse, "排课");
        }

        int capacity = request.getCapacity() == null ? lesson.getCapacity() : request.getCapacity();
        if (capacity < 1) {
            throw new BizException("场次容量必须大于 0");
        }
        if (capacity > lesson.getCapacity()) {
            throw new BizException("场次容量 " + capacity + " 超过课程「" + lesson.getName()
                    + "」的容量上限 " + lesson.getCapacity());
        }

        LocalDate date = request.getSessionDate();
        for (LessonSession exist : sessionRepository.findByCoachIdAndSessionDate(coach.getId(), date)) {
            if (EquestrianDict.SESSION_CANCELED.equals(exist.getStatus())) {
                continue;
            }
            if (EquestrianDict.overlap(startTime, endTime, exist.getStartTime(), exist.getEndTime())) {
                throw new BizException("教练「" + coach.getName() + "」在 " + date + " 的 "
                        + exist.getStartTime() + "-" + exist.getEndTime()
                        + " 已有排期，同一教练同一时段不能重叠排课");
            }
        }

        LessonSession session = new LessonSession();
        session.setLessonId(lesson.getId());
        session.setCoachId(coach.getId());
        session.setHorseId(request.getHorseId());
        session.setSessionDate(date);
        session.setStartTime(startTime);
        session.setEndTime(endTime);
        session.setCapacity(capacity);
        // 默认值放在 service，实体上不写默认值
        session.setBookedCount(0);
        session.setStatus(EquestrianDict.SESSION_SCHEDULED);
        session.setHealthAffected(false);

        LessonSession saved = sessionRepository.save(session);
        // 新出现的课程变更进入当前最新 OPEN 班次的交接清单（同事务，挂入失败整笔回滚）
        eventPublisher.publishEvent(new SessionChangedEvent(
                saved.getId(), EquestrianDict.CHANGE_NEW, trimToNull(request.getOperatorName())));
        return toView(saved);
    }

    /** 取消排期：已约满或已排期的场次都可以取消，已取消的不能再取消 */
    @Transactional
    public SessionView cancel(Long id, String operatorName) {
        LessonSession session = require(id);
        if (EquestrianDict.SESSION_CANCELED.equals(session.getStatus())) {
            throw new BizException("排期（" + session.getSessionDate() + " " + session.getStartTime()
                    + "）已经是「已取消」状态");
        }
        if (session.getBookedCount() != null && session.getBookedCount() > 0) {
            throw new BizException("排期（" + session.getSessionDate() + " " + session.getStartTime()
                    + "）已有 " + session.getBookedCount() + " 条有效预约，请先取消会员的骑乘记录");
        }
        session.setStatus(EquestrianDict.SESSION_CANCELED);
        LessonSession saved = sessionRepository.save(session);
        // 取消也是课程变更：挂入当前最新 OPEN 班次的交接清单（同事务）
        eventPublisher.publishEvent(new SessionChangedEvent(
                saved.getId(), EquestrianDict.CHANGE_CANCELED, trimToNull(operatorName)));
        return toView(saved);
    }

    // ---------------- 视图组装 ----------------

    Lesson requireLesson(Long lessonId) {
        if (lessonId == null) {
            throw new BizException("请先选择课程");
        }
        return lessonRepository.findById(lessonId)
                .orElseThrow(() -> new BizException("课程不存在，编号：" + lessonId));
    }

    private LessonView toLessonView(Lesson lesson) {
        return new LessonView(
                lesson.getId(),
                lesson.getLessonNo(),
                lesson.getName(),
                lesson.getCategory(),
                EquestrianDict.categoryName(lesson.getCategory()),
                lesson.getCapacity(),
                lesson.getDurationMin(),
                lesson.getPrice(),
                lesson.getTimesCost(),
                lesson.getStatus(),
                EquestrianDict.lessonStatusName(lesson.getStatus()));
    }

    private CoachView toCoachView(Coach coach) {
        return new CoachView(
                coach.getId(),
                coach.getCoachNo(),
                coach.getName(),
                coach.getLevel(),
                EquestrianDict.coachLevelName(coach.getLevel()),
                coach.getPhone(),
                coach.getStatus(),
                EquestrianDict.coachStatusName(coach.getStatus()));
    }

    SessionView toView(LessonSession session) {
        Lesson lesson = lessonRepository.findById(session.getLessonId()).orElse(null);
        Coach coach = coachRepository.findById(session.getCoachId()).orElse(null);
        String horseNo = null;
        String horseName = null;
        if (session.getHorseId() != null) {
            Horse horse = horseService.require(session.getHorseId());
            horseNo = horse.getHorseNo();
            horseName = horse.getName();
        }
        int booked = session.getBookedCount() == null ? 0 : session.getBookedCount();
        int capacity = session.getCapacity() == null ? 0 : session.getCapacity();
        return new SessionView(
                session.getId(),
                session.getLessonId(),
                lesson == null ? "" : lesson.getLessonNo(),
                lesson == null ? "课程已删除" : lesson.getName(),
                lesson == null ? "" : lesson.getCategory(),
                lesson == null ? "" : EquestrianDict.categoryName(lesson.getCategory()),
                session.getCoachId(),
                coach == null ? "教练已删除" : coach.getName(),
                session.getHorseId(),
                horseNo,
                horseName,
                session.getSessionDate(),
                session.getStartTime(),
                session.getEndTime(),
                capacity,
                booked,
                Math.max(capacity - booked, 0),
                session.getStatus(),
                EquestrianDict.sessionStatusName(session.getStatus()),
                Boolean.TRUE.equals(session.getHealthAffected()),
                handoverService.sessionHandoverSummary(session.getId()));
    }

    private String trimToNull(String text) {
        if (text == null) {
            return null;
        }
        String trimmed = text.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
