package com.equestrian.club.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.equestrian.club.common.BizException;
import com.equestrian.club.dict.EquestrianDict;
import com.equestrian.club.domain.Horse;
import com.equestrian.club.domain.HorseRepository;
import com.equestrian.club.domain.HealthEvent;
import com.equestrian.club.domain.HealthEventRepository;
import com.equestrian.club.domain.Lesson;
import com.equestrian.club.domain.LessonRepository;
import com.equestrian.club.domain.LessonSession;
import com.equestrian.club.domain.LessonSessionRepository;
import com.equestrian.club.domain.Coach;
import com.equestrian.club.domain.CoachRepository;
import com.equestrian.club.domain.Member;
import com.equestrian.club.domain.MemberRepository;
import com.equestrian.club.domain.RidingRecord;
import com.equestrian.club.domain.RidingRecordRepository;
import com.equestrian.club.dto.HorseRequest;
import com.equestrian.club.dto.StatusRequest;
import com.equestrian.club.dto.view.HorseCalendarView;
import com.equestrian.club.dto.view.HorseView;

/**
 * 马匹档案模块：编号唯一、在役状态机、退役禁止排课。
 * 马匹基础信息与健康档案由 @SecondaryTable 拆到两张表，这一层对调用方不做区分。
 */
@Service
public class HorseService {

    private final HorseRepository horseRepository;
    private final HealthEventRepository healthEventRepository;
    private final LessonSessionRepository sessionRepository;
    private final LessonRepository lessonRepository;
    private final CoachRepository coachRepository;
    private final MemberRepository memberRepository;
    private final RidingRecordRepository recordRepository;

    public HorseService(HorseRepository horseRepository,
            HealthEventRepository healthEventRepository,
            LessonSessionRepository sessionRepository,
            LessonRepository lessonRepository,
            CoachRepository coachRepository,
            MemberRepository memberRepository,
            RidingRecordRepository recordRepository) {
        this.horseRepository = horseRepository;
        this.healthEventRepository = healthEventRepository;
        this.sessionRepository = sessionRepository;
        this.lessonRepository = lessonRepository;
        this.coachRepository = coachRepository;
        this.memberRepository = memberRepository;
        this.recordRepository = recordRepository;
    }

    // ---------------- 查询 ----------------

    @Transactional(readOnly = true)
    public List<HorseView> list() {
        return horseRepository.findAllByOrderByIdAsc().stream().map(this::toView).toList();
    }

    @Transactional(readOnly = true)
    public HorseView detail(Long id) {
        return toView(require(id));
    }

    /** 供其它模块复用的取马方法：可空外键必须先判 null 再查库 */
    @Transactional(readOnly = true)
    public Horse require(Long horseId) {
        if (horseId == null) {
            throw new BizException("请先选择马匹");
        }
        return horseRepository.findById(horseId)
                .orElseThrow(() -> new BizException("马匹不存在，编号：" + horseId));
    }

    /** 退役 / 休养的马匹不允许被排进任何骑乘安排 */
    public void requireRideable(Horse horse, String scene) {
        if (EquestrianDict.HORSE_RETIRED.equals(horse.getStatus())) {
            throw new BizException("马匹「" + horse.getName() + "」已退役，禁止" + scene);
        }
        if (EquestrianDict.HORSE_RESTING.equals(horse.getStatus())) {
            throw new BizException("马匹「" + horse.getName() + "」正在休养，暂不可" + scene);
        }
    }

    // ---------------- 新增 / 修改 ----------------

    @Transactional
    public HorseView create(HorseRequest request) {
        if (request.getHorseNo() == null || request.getHorseNo().isBlank()) {
            throw new BizException("马匹编号不能为空");
        }
        String horseNo = request.getHorseNo().trim();
        if (horseRepository.existsByHorseNo(horseNo)) {
            throw new BizException("马匹编号「" + horseNo + "」已存在，编号必须唯一");
        }
        if (request.getName() == null || request.getName().isBlank()) {
            throw new BizException("马匹名称不能为空");
        }

        Horse horse = new Horse();
        horse.setHorseNo(horseNo);
        horse.setName(request.getName().trim());
        horse.setBreed(request.getBreed());
        horse.setGender(request.getGender());
        horse.setBirthYear(request.getBirthYear());
        horse.setHealthNote(request.getHealthNote());
        horse.setLastCheckDate(request.getLastCheckDate());
        horse.setVaccineCount(request.getVaccineCount());
        horse.setWeightKg(request.getWeightKg());

        // 默认值只在这里给，实体上不写默认值，避免 PUT 局部更新刷回默认值
        String status = request.getStatus() == null ? EquestrianDict.HORSE_ACTIVE : request.getStatus();
        if (!EquestrianDict.isValidHorseStatus(status)) {
            throw new BizException("在役状态取值不合法：" + status + "，只支持 ACTIVE / RESTING / RETIRED");
        }
        horse.setStatus(status);
        String rideLevel = request.getRideLevel() == null ? "BEGINNER_SAFE" : request.getRideLevel();
        if (!EquestrianDict.isValidRideLevel(rideLevel)) {
            throw new BizException("骑乘等级取值不合法：" + rideLevel);
        }
        horse.setRideLevel(rideLevel);

        return toView(horseRepository.save(horse));
    }

    /**
     * 局部更新：只覆盖请求里非空的字段，基础信息与健康档案一起改。
     */
    @Transactional
    public HorseView update(Long id, HorseRequest request) {
        Horse horse = require(id);

        if (request.getHorseNo() != null && !request.getHorseNo().isBlank()) {
            String horseNo = request.getHorseNo().trim();
            if (!horseNo.equals(horse.getHorseNo())) {
                if (horseRepository.existsByHorseNo(horseNo)) {
                    throw new BizException("马匹编号「" + horseNo + "」已存在，编号必须唯一");
                }
                horse.setHorseNo(horseNo);
            }
        }
        if (request.getName() != null && !request.getName().isBlank()) {
            horse.setName(request.getName().trim());
        }
        if (request.getBreed() != null) {
            horse.setBreed(request.getBreed());
        }
        if (request.getGender() != null) {
            horse.setGender(request.getGender());
        }
        if (request.getBirthYear() != null) {
            horse.setBirthYear(request.getBirthYear());
        }
        if (request.getRideLevel() != null) {
            if (!EquestrianDict.isValidRideLevel(request.getRideLevel())) {
                throw new BizException("骑乘等级取值不合法：" + request.getRideLevel());
            }
            horse.setRideLevel(request.getRideLevel());
        }
        if (request.getStatus() != null) {
            // 在役状态只能由两条受控通道驱动：马匹状态机接口 / 健康事件复训放行。
            // 不允许通过编辑档案直接改，避免绕过状态机与「未闭环事件不能恢复在役」校验。
            throw new BizException("在役状态由状态流转 / 健康事件复训放行驱动，不能通过编辑档案直接修改");
        }
        // 副表 horse_health 的字段
        if (request.getLastCheckDate() != null) {
            horse.setLastCheckDate(request.getLastCheckDate());
        }
        if (request.getVaccineCount() != null) {
            horse.setVaccineCount(request.getVaccineCount());
        }
        if (request.getWeightKg() != null) {
            horse.setWeightKg(request.getWeightKg());
        }
        if (request.getHealthNote() != null) {
            horse.setHealthNote(request.getHealthNote());
        }

        return toView(horseRepository.save(horse));
    }

    /**
     * 在役状态机流转：在役 <-> 休养，两者都可转退役；退役是终态，不可回退。
     */
    @Transactional
    public HorseView changeStatus(Long id, StatusRequest request) {
        Horse horse = require(id);
        String target = request == null ? null : request.getStatus();
        if (target == null || target.isBlank()) {
            throw new BizException("请提供要流转的目标状态");
        }
        if (!EquestrianDict.isValidHorseStatus(target)) {
            throw new BizException("在役状态取值不合法：" + target + "，只支持 ACTIVE / RESTING / RETIRED");
        }
        String current = horse.getStatus();
        if (current.equals(target)) {
            throw new BizException("马匹「" + horse.getName() + "」已经是「"
                    + EquestrianDict.horseStatusName(current) + "」状态，无需重复变更");
        }
        if (!EquestrianDict.canTransferHorseStatus(current, target)) {
            if (EquestrianDict.HORSE_RETIRED.equals(current)) {
                throw new BizException("马匹「" + horse.getName() + "」已退役，退役为终态，不能再流转为「"
                        + EquestrianDict.horseStatusName(target) + "」");
            }
            throw new BizException("马匹状态不能从「" + EquestrianDict.horseStatusName(current)
                    + "」直接流转为「" + EquestrianDict.horseStatusName(target) + "」");
        }
        // 休养 -> 在役只能走健康事件的「复训放行」闭环：只要还有未关闭事件，手工恢复一律拒绝，
        // 避免有人用马匹档案的状态流转把休养 / 复查规则整个绕过。
        if (EquestrianDict.HORSE_RESTING.equals(current) && EquestrianDict.HORSE_ACTIVE.equals(target)
                && healthEventRepository.existsByHorseIdAndStatusNot(id, EquestrianDict.EVENT_CLOSED)) {
            throw new BizException("马匹「" + horse.getName()
                    + "」仍有未关闭的健康事件，不能直接恢复在役，请在健康事件处置台完成复查并由负责人复训放行");
        }
        horse.setStatus(target);
        return toView(horseRepository.save(horse));
    }

    // ---------------- 训练日历 ----------------

    /**
     * 取某匹马未来 N 天的训练日历：行是固定时段，列是日期。
     */
    @Transactional(readOnly = true)
    public HorseCalendarView calendar(Long horseId, Integer days) {
        Horse horse = require(horseId);
        int span = days == null || days < 1 ? EquestrianDict.CALENDAR_DAYS : days;
        if (span > EquestrianDict.CALENDAR_MAX_DAYS) {
            span = EquestrianDict.CALENDAR_MAX_DAYS;
        }

        LocalDate from = LocalDate.now();
        LocalDate to = from.plusDays(span - 1L);
        List<LocalDate> dates = new ArrayList<>();
        for (int i = 0; i < span; i++) {
            dates.add(from.plusDays(i));
        }

        List<LessonSession> sessions =
                sessionRepository.findByHorseIdAndSessionDateBetweenOrderBySessionDateAscStartTimeAsc(horseId, from, to);

        List<HorseCalendarView.Cell> cells = new ArrayList<>();
        for (LessonSession session : sessions) {
            Lesson lesson = lessonRepository.findById(session.getLessonId()).orElse(null);
            Coach coach = coachRepository.findById(session.getCoachId()).orElse(null);
            List<HorseCalendarView.CellRecord> records = new ArrayList<>();
            for (RidingRecord record : recordRepository.findBySessionIdOrderByIdAsc(session.getId())) {
                // 已取消的记录不占日历格子
                if (EquestrianDict.RECORD_CANCELED.equals(record.getStatus())) {
                    continue;
                }
                records.add(new HorseCalendarView.CellRecord(
                        record.getId(),
                        record.getMemberId(),
                        memberNameOf(record.getMemberId()),
                        record.getStatus(),
                        EquestrianDict.recordStatusName(record.getStatus())));
            }
            cells.add(new HorseCalendarView.Cell(
                    session.getSessionDate(),
                    session.getStartTime(),
                    session.getEndTime(),
                    session.getId(),
                    lesson == null ? "" : lesson.getName(),
                    lesson == null ? "" : lesson.getCategory(),
                    coach == null ? "" : coach.getName(),
                    session.getCapacity(),
                    session.getBookedCount(),
                    session.getStatus(),
                    EquestrianDict.sessionStatusName(session.getStatus()),
                    Boolean.TRUE.equals(session.getHealthAffected()),
                    records));
        }

        return new HorseCalendarView(
                horse.getId(),
                horse.getHorseNo(),
                horse.getName(),
                horse.getStatus(),
                EquestrianDict.horseStatusName(horse.getStatus()),
                horse.getRideLevel(),
                EquestrianDict.rideLevelName(horse.getRideLevel()),
                dates,
                EquestrianDict.TIME_SLOTS,
                cells);
    }

    private String memberNameOf(Long memberId) {
        if (memberId == null) {
            return "";
        }
        return memberRepository.findById(memberId).map(Member::getName).orElse("已删除会员");
    }

    // ---------------- 视图组装 ----------------

    private HorseView toView(Horse horse) {
        Integer age = horse.getBirthYear() == null ? null : LocalDate.now().getYear() - horse.getBirthYear();
        return new HorseView(
                horse.getId(),
                horse.getHorseNo(),
                horse.getName(),
                horse.getBreed(),
                horse.getGender(),
                horse.getBirthYear(),
                age,
                horse.getStatus(),
                EquestrianDict.horseStatusName(horse.getStatus()),
                horse.getRideLevel(),
                EquestrianDict.rideLevelName(horse.getRideLevel()),
                horse.getLastCheckDate(),
                horse.getVaccineCount(),
                horse.getWeightKg(),
                horse.getHealthNote(),
                horse.getHealthVersion(),
                horse.getCreatedAt(),
                horse.getUpdatedAt());
    }
}
