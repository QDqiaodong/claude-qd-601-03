package com.equestrian.club.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.equestrian.club.common.BizException;
import com.equestrian.club.dict.EquestrianDict;
import com.equestrian.club.domain.Coach;
import com.equestrian.club.domain.CoachRepository;
import com.equestrian.club.domain.Horse;
import com.equestrian.club.domain.Lesson;
import com.equestrian.club.domain.LessonSession;
import com.equestrian.club.domain.LessonSessionRepository;
import com.equestrian.club.domain.Member;
import com.equestrian.club.domain.MemberRepository;
import com.equestrian.club.domain.RidingRecord;
import com.equestrian.club.domain.RidingRecordRepository;
import com.equestrian.club.dto.RidingRequest;
import com.equestrian.club.dto.view.MemberView;
import com.equestrian.club.dto.view.RecordView;

/**
 * 会员与骑乘记录模块：会员卡余额 / 次数、记录必须关联已排期的课程、同一会员同一天同一时段不可重复约。
 */
@Service
public class RidingService {

    private final MemberRepository memberRepository;
    private final RidingRecordRepository recordRepository;
    private final LessonSessionRepository sessionRepository;
    private final CoachRepository coachRepository;
    private final SessionService sessionService;
    private final HorseService horseService;

    public RidingService(MemberRepository memberRepository,
            RidingRecordRepository recordRepository,
            LessonSessionRepository sessionRepository,
            CoachRepository coachRepository,
            SessionService sessionService,
            HorseService horseService) {
        this.memberRepository = memberRepository;
        this.recordRepository = recordRepository;
        this.sessionRepository = sessionRepository;
        this.coachRepository = coachRepository;
        this.sessionService = sessionService;
        this.horseService = horseService;
    }

    // ---------------- 查询 ----------------

    @Transactional(readOnly = true)
    public List<MemberView> listMembers() {
        return memberRepository.findAllByOrderByIdAsc().stream().map(this::toMemberView).toList();
    }

    @Transactional(readOnly = true)
    public List<RecordView> listRecords() {
        return recordRepository.findAllByOrderByIdDesc().stream().map(this::toRecordView).toList();
    }

    @Transactional(readOnly = true)
    public List<RecordView> recordsOfMember(Long memberId) {
        if (memberId == null) {
            throw new BizException("请先选择会员");
        }
        return recordRepository.findByMemberIdOrderByRecordDateDescIdDesc(memberId)
                .stream().map(this::toRecordView).toList();
    }

    @Transactional(readOnly = true)
    public List<RecordView> recordsOfSession(Long sessionId) {
        LessonSession session = sessionService.require(sessionId);
        return recordRepository.findBySessionIdOrderByIdAsc(session.getId())
                .stream().map(this::toRecordView).toList();
    }

    // ---------------- 预约 ----------------

    /**
     * 预约：一次把课程容量、进阶课门槛、会员卡次数与余额、同时段重复、马匹可骑状态全部校验完。
     */
    @Transactional
    public RecordView book(RidingRequest request) {
        if (request == null || request.getMemberId() == null) {
            throw new BizException("预约失败：请选择会员");
        }
        if (request.getSessionId() == null) {
            throw new BizException("预约失败：请选择要预约的课程场次");
        }

        Member member = memberRepository.findById(request.getMemberId())
                .orElseThrow(() -> new BizException("会员不存在，编号：" + request.getMemberId()));

        // 骑乘记录必须关联一条已排期的课程
        LessonSession session = sessionService.require(request.getSessionId());
        Lesson lesson = sessionService.requireLesson(session.getLessonId());

        if (EquestrianDict.SESSION_CANCELED.equals(session.getStatus())) {
            throw new BizException("排期（" + session.getSessionDate() + " " + session.getStartTime() + " "
                    + lesson.getName() + "）已取消，不能预约");
        }
        if (!EquestrianDict.MEMBER_NORMAL.equals(member.getStatus())) {
            throw new BizException("会员「" + member.getName() + "」的会员卡处于「"
                    + EquestrianDict.memberStatusName(member.getStatus()) + "」状态，无法预约");
        }

        // 课程容量
        int booked = (int) recordRepository.countBySessionIdAndStatusNot(session.getId(),
                EquestrianDict.RECORD_CANCELED);
        int capacity = session.getCapacity();
        if (booked >= capacity) {
            throw new BizException("排期（" + session.getSessionDate() + " " + session.getStartTime() + " "
                    + lesson.getName() + "）已满员（" + booked + "/" + capacity + "），无法继续预约");
        }

        // 未通过初级考核的会员不能约进阶课 / 私教课
        if (EquestrianDict.isAdvancedCategory(lesson.getCategory())
                && !Boolean.TRUE.equals(member.getPassedBasic())) {
            throw new BizException("会员「" + member.getName() + "」尚未通过初级考核，不能预约"
                    + EquestrianDict.categoryName(lesson.getCategory()) + "「" + lesson.getName() + "」");
        }

        // 会员卡次数
        int timesCost = lesson.getTimesCost();
        int cardTimes = member.getCardTimes() == null ? 0 : member.getCardTimes();
        if (cardTimes < timesCost) {
            throw new BizException("会员「" + member.getName() + "」剩余次数 " + cardTimes
                    + " 次，不足以预约「" + lesson.getName() + "」（需 " + timesCost + " 次）");
        }

        // 会员卡余额（按会员等级折扣折算）
        BigDecimal fee = lesson.getPrice()
                .multiply(EquestrianDict.memberDiscount(member.getLevel()))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal balance = member.getCardBalance() == null ? BigDecimal.ZERO : member.getCardBalance();
        if (balance.compareTo(fee) < 0) {
            throw new BizException("会员「" + member.getName() + "」卡内余额 " + balance
                    + " 元，不足以支付「" + lesson.getName() + "」的 " + fee + " 元");
        }

        // 同一会员同一天同一时段不能重复约
        List<RidingRecord> duplicated = recordRepository.findByMemberIdAndRecordDateAndStartTimeAndStatusNot(
                member.getId(), session.getSessionDate(), session.getStartTime(), EquestrianDict.RECORD_CANCELED);
        if (!duplicated.isEmpty()) {
            throw new BizException("会员「" + member.getName() + "」在 " + session.getSessionDate() + " "
                    + session.getStartTime() + " 已经约过课，同一天同一时段不能重复预约");
        }

        // 马匹：请求里指定了就用指定的，否则用排期自带的；退役 / 休养的马禁止安排骑乘
        Long horseId = request.getHorseId() != null ? request.getHorseId() : session.getHorseId();
        if (horseId != null) {
            Horse horse = horseService.require(horseId);
            horseService.requireRideable(horse, "安排骑乘课程");
        }

        // 扣次数、扣余额、场次人数 +1
        member.setCardTimes(cardTimes - timesCost);
        member.setCardBalance(balance.subtract(fee));
        memberRepository.save(member);

        session.setBookedCount(booked + 1);
        if (booked + 1 >= capacity) {
            session.setStatus(EquestrianDict.SESSION_FULL);
        }
        sessionRepository.save(session);

        RidingRecord record = new RidingRecord();
        record.setMemberId(member.getId());
        record.setSessionId(session.getId());
        record.setHorseId(horseId);
        record.setRecordDate(session.getSessionDate());
        record.setStartTime(session.getStartTime());
        record.setEndTime(session.getEndTime());
        record.setFee(fee);
        record.setTimesUsed(timesCost);
        record.setRemark(request.getRemark());
        // 默认值放在 service，实体上不写默认值
        record.setStatus(EquestrianDict.RECORD_BOOKED);

        return toRecordView(recordRepository.save(record));
    }

    /** 取消预约：退回次数与余额，场次人数 -1 */
    @Transactional
    public RecordView cancel(Long id) {
        RidingRecord record = require(id);
        if (!EquestrianDict.RECORD_BOOKED.equals(record.getStatus())) {
            throw new BizException("骑乘记录（编号 " + id + "）当前为「"
                    + EquestrianDict.recordStatusName(record.getStatus()) + "」，只有已预约的记录可以取消");
        }
        Member member = memberRepository.findById(record.getMemberId())
                .orElseThrow(() -> new BizException("会员不存在，编号：" + record.getMemberId()));
        member.setCardTimes((member.getCardTimes() == null ? 0 : member.getCardTimes()) + record.getTimesUsed());
        member.setCardBalance((member.getCardBalance() == null ? BigDecimal.ZERO : member.getCardBalance())
                .add(record.getFee()));
        memberRepository.save(member);

        LessonSession session = sessionRepository.findById(record.getSessionId()).orElse(null);
        if (session != null) {
            int booked = session.getBookedCount() == null ? 0 : session.getBookedCount();
            session.setBookedCount(Math.max(booked - 1, 0));
            if (EquestrianDict.SESSION_FULL.equals(session.getStatus())) {
                session.setStatus(EquestrianDict.SESSION_SCHEDULED);
            }
            sessionRepository.save(session);
        }

        record.setStatus(EquestrianDict.RECORD_CANCELED);
        return toRecordView(recordRepository.save(record));
    }

    /** 完成骑乘：不退款，只推进状态 */
    @Transactional
    public RecordView complete(Long id) {
        RidingRecord record = require(id);
        if (EquestrianDict.RECORD_COMPLETED.equals(record.getStatus())) {
            throw new BizException("骑乘记录（编号 " + id + "）已经是「已完成」状态");
        }
        if (EquestrianDict.RECORD_CANCELED.equals(record.getStatus())) {
            throw new BizException("骑乘记录（编号 " + id + "）已取消，不能置为已完成");
        }
        record.setStatus(EquestrianDict.RECORD_COMPLETED);
        return toRecordView(recordRepository.save(record));
    }

    private RidingRecord require(Long id) {
        if (id == null) {
            throw new BizException("请先选择骑乘记录");
        }
        return recordRepository.findById(id)
                .orElseThrow(() -> new BizException("骑乘记录不存在，编号：" + id));
    }

    // ---------------- 视图组装 ----------------

    private MemberView toMemberView(Member member) {
        return new MemberView(
                member.getId(),
                member.getMemberNo(),
                member.getName(),
                member.getPhone(),
                member.getLevel(),
                EquestrianDict.memberLevelName(member.getLevel()),
                member.getCardBalance(),
                member.getCardTimes(),
                member.getPassedBasic(),
                Boolean.TRUE.equals(member.getPassedBasic()) ? "已通过" : "未通过",
                member.getStatus(),
                EquestrianDict.memberStatusName(member.getStatus()));
    }

    private RecordView toRecordView(RidingRecord record) {
        Member member = memberRepository.findById(record.getMemberId()).orElse(null);
        LessonSession session = sessionRepository.findById(record.getSessionId()).orElse(null);
        Lesson lesson = session == null ? null
                : sessionService.requireLesson(session.getLessonId());
        Coach coach = session == null ? null
                : coachRepository.findById(session.getCoachId()).orElse(null);
        String horseNo = null;
        String horseName = null;
        if (record.getHorseId() != null) {
            Horse horse = horseService.require(record.getHorseId());
            horseNo = horse.getHorseNo();
            horseName = horse.getName();
        }
        return new RecordView(
                record.getId(),
                record.getMemberId(),
                member == null ? "" : member.getMemberNo(),
                member == null ? "会员已删除" : member.getName(),
                record.getSessionId(),
                lesson == null ? "课程已删除" : lesson.getName(),
                lesson == null ? "" : lesson.getCategory(),
                lesson == null ? "" : EquestrianDict.categoryName(lesson.getCategory()),
                coach == null ? "教练已删除" : coach.getName(),
                record.getHorseId(),
                horseNo,
                horseName,
                record.getRecordDate(),
                record.getStartTime(),
                record.getEndTime(),
                record.getStatus(),
                EquestrianDict.recordStatusName(record.getStatus()),
                record.getFee(),
                record.getTimesUsed(),
                record.getRemark(),
                record.getCreatedAt());
    }
}
