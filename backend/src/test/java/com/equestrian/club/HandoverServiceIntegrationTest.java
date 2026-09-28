package com.equestrian.club;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import com.equestrian.club.common.BizException;
import com.equestrian.club.common.ConflictException;
import com.equestrian.club.dict.EquestrianDict;
import com.equestrian.club.domain.Coach;
import com.equestrian.club.domain.CoachRepository;
import com.equestrian.club.domain.DutyShift;
import com.equestrian.club.domain.DutyShiftRepository;
import com.equestrian.club.domain.HandoverAcceptanceRepository;
import com.equestrian.club.domain.HandoverItem;
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
import com.equestrian.club.dto.ShiftCloseRequest;
import com.equestrian.club.dto.ShiftCreateRequest;
import com.equestrian.club.dto.view.CloseBlockerView;
import com.equestrian.club.dto.view.ItemView;
import com.equestrian.club.dto.view.ShiftView;
import com.equestrian.club.service.HandoverService;
import com.equestrian.club.service.SessionService;

/**
 * 值班交接链路集成测试（H2 MySQL 兼容模式 + 真实事务 / 事件监听）：
 * 覆盖开班快照、跨班次未承接不消失、逐项承接幂等、权限后端校验、
 * 关班逐项延期原子性、课程变更实时入队、并发版本 409、已承接终态保护。
 */
@SpringBootTest
class HandoverServiceIntegrationTest {

    @Autowired
    private HandoverService handoverService;
    @Autowired
    private SessionService sessionService;
    @Autowired
    private DutyShiftRepository shiftRepository;
    @Autowired
    private HandoverItemRepository itemRepository;
    @Autowired
    private HandoverAcceptanceRepository acceptanceRepository;
    @Autowired
    private HandoverItemLogRepository logRepository;
    @Autowired
    private HorseRepository horseRepository;
    @Autowired
    private StallRepository stallRepository;
    @Autowired
    private CoachRepository coachRepository;
    @Autowired
    private LessonRepository lessonRepository;
    @Autowired
    private LessonSessionRepository sessionRepository;
    @Autowired
    private HealthEventRepository eventRepository;

    @BeforeEach
    void cleanUp() {
        logRepository.deleteAllInBatch();
        acceptanceRepository.deleteAllInBatch();
        itemRepository.deleteAllInBatch();
        shiftRepository.deleteAllInBatch();
        sessionRepository.deleteAllInBatch();
        eventRepository.deleteAllInBatch();
        stallRepository.deleteAllInBatch();
        horseRepository.deleteAllInBatch();
        coachRepository.deleteAllInBatch();
        lessonRepository.deleteAllInBatch();
    }

    private void seedBasics() {
        Coach coach = new Coach();
        coach.setCoachNo("C-T1");
        coach.setName("林婉");
        coach.setLevel("JUNIOR");
        coach.setStatus(EquestrianDict.COACH_ON_DUTY);
        coachRepository.save(coach);

        Lesson lesson = new Lesson();
        lesson.setLessonNo("L-T1");
        lesson.setName("初级骑乘入门");
        lesson.setCategory(EquestrianDict.CATEGORY_BASIC);
        lesson.setCapacity(6);
        lesson.setDurationMin(60);
        lesson.setPrice(java.math.BigDecimal.valueOf(380));
        lesson.setTimesCost(1);
        lesson.setStatus(EquestrianDict.LESSON_ON);
        lessonRepository.save(lesson);

        Horse horse = new Horse();
        horse.setHorseNo("H-T1");
        horse.setName("追风");
        horse.setStatus(EquestrianDict.HORSE_RESTING);
        horse.setRideLevel("BEGINNER_SAFE");
        horse.setHealthVersion(1L);
        horseRepository.save(horse);

        HealthEvent event = new HealthEvent();
        event.setEventNo("HE-T1");
        event.setHorseId(horse.getId());
        event.setOccurredAt(java.time.LocalDateTime.now().minusDays(1));
        event.setSeverity("MEDIUM");
        event.setSymptom("跛行");
        event.setTreatmentAdvice("休养");
        event.setExpectedReviewDate(LocalDate.now().plusDays(2));
        event.setStatus(EquestrianDict.EVENT_REVIEW_PENDING);
        event.setRegisteredVersion(1L);
        event.setCreatedBy("tester");
        event.setCreatedRole("STAFF");
        eventRepository.save(event);

        Stall stall = new Stall();
        stall.setStallNo("STM-T1");
        stall.setBarnName("A 区马房");
        stall.setStatus(EquestrianDict.STALL_MAINTENANCE);
        stallRepository.save(stall);
    }

    private ShiftCreateRequest shiftRequest(String leader, String requestId) {
        ShiftCreateRequest request = new ShiftCreateRequest();
        request.setOperatorName(leader);
        request.setOperatorRole(EquestrianDict.ROLE_MANAGER);
        request.setRequestId(requestId);
        return request;
    }

    private com.equestrian.club.dto.SessionRequest sessionRequest(LocalDate date, String start) {
        com.equestrian.club.dto.SessionRequest request = new com.equestrian.club.dto.SessionRequest();
        request.setLessonId(lessonRepository.findAllByOrderByIdAsc().get(0).getId());
        request.setCoachId(coachRepository.findAllByOrderByIdAsc().get(0).getId());
        request.setSessionDate(date);
        request.setStartTime(start);
        request.setEndTime(EquestrianDict.endOfSlot(start));
        request.setCapacity(6);
        request.setOperatorName("赵崇");
        return request;
    }

    // ---------------- 开班快照 ----------------

    @Test
    void createShift_generatesSnapshotFromSessionsHorsesStalls() {
        seedBasics();
        // 今天一场课
        sessionService.create(sessionRequest(LocalDate.now(), "09:00"));

        ShiftView shift = handoverService.createShift(shiftRequest("陈骁", "req-open-1"));

        assertEquals(EquestrianDict.SHIFT_OPEN, shift.status());
        // 今日课程 1 + 休养马 1 + 维护栏位 1 = 3
        assertEquals(3, shift.items().size());
        assertTrue(shift.items().stream().anyMatch(i -> EquestrianDict.ITEM_TYPE_HORSE.equals(i.itemType())));
        assertTrue(shift.items().stream().anyMatch(i -> EquestrianDict.ITEM_TYPE_STALL.equals(i.itemType())));
        assertTrue(shift.items().stream().anyMatch(i -> EquestrianDict.ITEM_TYPE_SESSION.equals(i.itemType())));
        // 全部待承接、未分派
        assertTrue(shift.items().stream().allMatch(i -> EquestrianDict.ITEM_PENDING.equals(i.status())));
        assertTrue(shift.items().stream().allMatch(i -> i.targetShiftId() == null));
    }

    @Test
    void createShift_staffIsForbidden() {
        seedBasics();
        ShiftCreateRequest request = shiftRequest("小周", "req-staff-open");
        request.setOperatorRole(EquestrianDict.ROLE_STAFF);
        BizException ex = assertThrows(BizException.class, () -> handoverService.createShift(request));
        assertTrue(ex.getMessage().contains("只有值班负责人"));
        assertEquals(0, shiftRepository.count());
    }

    @Test
    void createShift_sameRequestIdIsIdempotent() {
        seedBasics();
        ShiftView first = handoverService.createShift(shiftRequest("陈骁", "req-open-idem"));
        ShiftView second = handoverService.createShift(shiftRequest("陈骁", "req-open-idem"));
        assertEquals(first.id(), second.id());
        assertEquals(1, shiftRepository.count());
    }

    // ---------------- 课程变更实时入队 ----------------

    @Test
    void newSessionWhileShiftOpen_entersCurrentShift() {
        seedBasics();
        ShiftView shift = handoverService.createShift(shiftRequest("陈骁", "req-open-2"));
        int before = shift.items().size();

        // 开班后新增一场「未来第 2 天」的课（不在今日快照窗口）
        sessionService.create(sessionRequest(LocalDate.now().plusDays(2), "14:00"));

        ShiftView reloaded = handoverService.detail(shift.id());
        assertEquals(before + 1, reloaded.items().size());
        assertTrue(reloaded.items().stream()
                .anyMatch(i -> EquestrianDict.CHANGE_NEW.equals(i.changeKind())));
    }

    @Test
    void newSessionWithoutOpenShift_createsNoItem() {
        seedBasics();
        sessionService.create(sessionRequest(LocalDate.now().plusDays(2), "14:00"));
        assertEquals(0, itemRepository.findAll().size());
    }

    // ---------------- 权限 ----------------

    @Test
    void staffCanOnlySupplementOwnedItems() {
        seedBasics();
        ShiftView shift = handoverService.createShift(shiftRequest("陈骁", "req-open-3"));
        HandoverItem horseItem = itemRepository.findByShiftIdOrderByIdDesc(shift.id()).stream()
                .filter(i -> EquestrianDict.ITEM_TYPE_HORSE.equals(i.getItemType()))
                .findFirst().orElseThrow();

        ItemSupplementRequest request = new ItemSupplementRequest();
        request.setOperatorName("林婉");
        request.setOperatorRole(EquestrianDict.ROLE_STAFF);
        request.setNote("我来看护");
        request.setExpectedVersion(0L);
        // 该事项没有分派给林婉
        BizException ex = assertThrows(BizException.class,
                () -> handoverService.supplement(horseItem.getId(), request));
        assertTrue(ex.getMessage().contains("普通教练只能补充"));

        // 负责人先把事项分派给林婉
        ItemUpdateRequest assign = new ItemUpdateRequest();
        assign.setOperatorName("陈骁");
        assign.setOperatorRole(EquestrianDict.ROLE_MANAGER);
        assign.setOwnerName("林婉");
        assign.setExpectedVersion(0L);
        handoverService.updateItem(horseItem.getId(), assign);

        request.setExpectedVersion(1L);
        ItemView updated = handoverService.supplement(horseItem.getId(), request);
        assertEquals(2L, updated.expectedVersion());
    }

    @Test
    void staffCanNotDispatchOrAccept() {
        seedBasics();
        ShiftView first = handoverService.createShift(shiftRequest("陈骁", "req-open-4"));
        ShiftView second = handoverService.createShift(shiftRequest("赵崇", "req-open-5"));
        HandoverItem item = itemRepository.findByShiftIdOrderByIdDesc(first.id()).get(0);

        ItemDispatchRequest dispatch = new ItemDispatchRequest();
        dispatch.setOperatorName("林婉");
        dispatch.setOperatorRole(EquestrianDict.ROLE_STAFF);
        dispatch.setExpectedVersion(0L);
        assertThrows(BizException.class, () -> handoverService.dispatch(item.getId(), dispatch));

        ItemAcceptRequest accept = new ItemAcceptRequest();
        accept.setOperatorName("林婉");
        accept.setOperatorRole(EquestrianDict.ROLE_STAFF);
        accept.setExpectedVersion(0L);
        assertThrows(BizException.class, () -> handoverService.accept(item.getId(), accept));
    }

    // ---------------- 分派 / 承接 / 终态保护 ----------------

    @Test
    void acceptedItemStaysVisibleAndCannotBeReverted() {
        seedBasics();
        ShiftView first = handoverService.createShift(shiftRequest("陈骁", "req-open-6"));
        ShiftView second = handoverService.createShift(shiftRequest("赵崇", "req-open-7"));
        HandoverItem item = itemRepository.findByShiftIdOrderByIdDesc(first.id()).get(0);

        // 陈骁分派给下一班
        ItemDispatchRequest dispatch = new ItemDispatchRequest();
        dispatch.setOperatorName("陈骁");
        dispatch.setOperatorRole(EquestrianDict.ROLE_MANAGER);
        dispatch.setExpectedVersion(0L);
        ItemView dispatched = handoverService.dispatch(item.getId(), dispatch);
        assertEquals(second.id(), dispatched.targetShiftId());

        // 下一班首页能看到未承接来项
        ShiftView board = handoverService.activeBoard();
        assertEquals(second.id(), board.id());
        assertTrue(board.incomingItems().stream().anyMatch(i -> i.id().equals(item.getId())));

        // 别班负责人不能承接
        ItemAcceptRequest wrongLeader = new ItemAcceptRequest();
        wrongLeader.setOperatorName("陈骁");
        wrongLeader.setOperatorRole(EquestrianDict.ROLE_MANAGER);
        wrongLeader.setExpectedVersion(1L);
        assertThrows(BizException.class, () -> handoverService.accept(item.getId(), wrongLeader));

        // 赵崇逐项承接
        ItemAcceptRequest accept = new ItemAcceptRequest();
        accept.setOperatorName("赵崇");
        accept.setOperatorRole(EquestrianDict.ROLE_MANAGER);
        accept.setNote("收到");
        accept.setExpectedVersion(1L);
        accept.setRequestId("req-accept-1");
        ItemView accepted = handoverService.accept(item.getId(), accept);
        assertEquals(EquestrianDict.ITEM_ACCEPTED, accepted.status());
        assertEquals(1, acceptanceRepository.count());

        // 重复确认（同一 requestId）不产生重复记录
        ItemView again = handoverService.accept(item.getId(), accept);
        assertEquals(EquestrianDict.ITEM_ACCEPTED, again.status());
        assertEquals(1, acceptanceRepository.count());

        // 无 requestId 的重复承接给业务错误
        ItemAcceptRequest dup = new ItemAcceptRequest();
        dup.setOperatorName("赵崇");
        dup.setOperatorRole(EquestrianDict.ROLE_MANAGER);
        dup.setExpectedVersion(2L);
        BizException ex = assertThrows(BizException.class, () -> handoverService.accept(item.getId(), dup));
        assertTrue(ex.getMessage().contains("不能重复承接"));

        // 已承接事项：旧班次负责人不能再补充 / 改回
        ItemSupplementRequest supplement = new ItemSupplementRequest();
        supplement.setOperatorName("陈骁");
        supplement.setOperatorRole(EquestrianDict.ROLE_MANAGER);
        supplement.setNote("想改回去");
        supplement.setExpectedVersion(2L);
        BizException revertEx = assertThrows(BizException.class,
                () -> handoverService.supplement(item.getId(), supplement));
        assertTrue(revertEx.getMessage().contains("不能被旧班次改回未处理"));

        // 承接后来项从未承接首页消失，但仍可在班次详情里看到
        ShiftView boardAfter = handoverService.activeBoard();
        assertFalse(boardAfter.incomingItems().stream().anyMatch(i -> i.id().equals(item.getId())));
    }

    // ---------------- 结班：逐项延期 + 原子性 ----------------

    @Test
    void closeShiftWithoutReasonsIsBlockedItemByItem() {
        seedBasics();
        ShiftView first = handoverService.createShift(shiftRequest("陈骁", "req-open-8"));
        handoverService.createShift(shiftRequest("赵崇", "req-open-9"));

        List<CloseBlockerView> blockers = handoverService.closeBlockers(first.id());
        assertFalse(blockers.isEmpty());
        // 未分派事项的拦截原因要具体
        assertTrue(blockers.stream().anyMatch(b -> b.reason().contains("尚未分派")));

        ShiftCloseRequest request = new ShiftCloseRequest();
        request.setOperatorName("陈骁");
        request.setOperatorRole(EquestrianDict.ROLE_MANAGER);
        request.setExpectedVersion(first.itemVersion());
        // 不给任何延期原因
        BizException ex = assertThrows(BizException.class,
                () -> handoverService.close(first.id(), request));
        assertTrue(ex.getMessage().contains("逐项记录延期原因"));

        // 被阻止后班次仍是 OPEN，事项没有被改
        DutyShift stillOpen = shiftRepository.findById(first.id()).orElseThrow();
        assertEquals(EquestrianDict.SHIFT_OPEN, stillOpen.getStatus());
        assertTrue(itemRepository.findByShiftIdOrderByIdDesc(first.id()).stream()
                .allMatch(i -> i.getDelayReason() == null && i.getTargetShiftId() == null));
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void closeShiftWithPartialReasonsRollsBackEverything() {
        seedBasics();
        ShiftView first = handoverService.createShift(shiftRequest("陈骁", "req-open-10"));
        handoverService.createShift(shiftRequest("赵崇", "req-open-11"));
        List<HandoverItem> items = itemRepository.findByShiftIdOrderByIdDesc(first.id());
        assertTrue(items.size() >= 2, "测试数据至少要有两个快照事项");

        // 只给第一项延期原因，第二项缺失 -> 整笔失败，不能留半条记录
        ShiftCloseRequest.DelayItem one = new ShiftCloseRequest.DelayItem();
        one.setItemId(items.get(0).getId());
        one.setReason("稍后处理");
        ShiftCloseRequest request = new ShiftCloseRequest();
        request.setOperatorName("陈骁");
        request.setOperatorRole(EquestrianDict.ROLE_MANAGER);
        request.setExpectedVersion(first.itemVersion());
        request.setDelays(List.of(one));
        assertThrows(BizException.class, () -> handoverService.close(first.id(), request));

        // 同事务内断言：班没结、第一项也没被写入延期 / 分派（回滚生效）
        DutyShift stillOpen = shiftRepository.findById(first.id()).orElseThrow();
        assertEquals(EquestrianDict.SHIFT_OPEN, stillOpen.getStatus());
        HandoverItem firstItem = itemRepository.findById(items.get(0).getId()).orElseThrow();
        assertNull(firstItem.getDelayReason());
        assertNull(firstItem.getTargetShiftId());
    }

    @Test
    void closeShiftWithAllReasonsDefersItemsAndKeepsThemOnNextBoard() {
        seedBasics();
        ShiftView first = handoverService.createShift(shiftRequest("陈骁", "req-open-12"));
        ShiftView second = handoverService.createShift(shiftRequest("赵崇", "req-open-13"));
        List<HandoverItem> items = itemRepository.findByShiftIdOrderByIdDesc(first.id());

        ShiftCloseRequest request = new ShiftCloseRequest();
        request.setOperatorName("陈骁");
        request.setOperatorRole(EquestrianDict.ROLE_MANAGER);
        request.setExpectedVersion(first.itemVersion());
        request.setDelays(items.stream().map(i -> {
            ShiftCloseRequest.DelayItem delay = new ShiftCloseRequest.DelayItem();
            delay.setItemId(i.getId());
            delay.setReason("顺延给赵崇班次处理：" + i.getItemNo());
            return delay;
        }).toList());

        ShiftView closed = handoverService.close(first.id(), request);
        assertEquals(EquestrianDict.SHIFT_CLOSED, closed.status());
        assertNotNull(closed.closedAt());

        // 全部未承接事项自动派给下一班、带着延期原因
        List<HandoverItem> deferred = itemRepository.findByShiftIdOrderByIdDesc(first.id());
        for (HandoverItem item : deferred) {
            assertEquals(second.id(), item.getTargetShiftId());
            assertNotNull(item.getDelayReason());
            assertEquals(EquestrianDict.ITEM_PENDING, item.getStatus());
        }

        // 未确认事项继续显示在下一班首页，不因班次结束消失
        ShiftView nextBoard = handoverService.activeBoard();
        assertEquals(second.id(), nextBoard.id());
        assertEquals(deferred.size(), nextBoard.incomingItems().size());
        assertTrue(nextBoard.incomingItems().stream().allMatch(i -> i.status().equals(EquestrianDict.ITEM_PENDING)));
    }

    @Test
    void closeShiftWithoutNextShiftRejectsUndispatchedItems() {
        seedBasics();
        ShiftView only = handoverService.createShift(shiftRequest("陈骁", "req-open-14"));
        List<HandoverItem> items = itemRepository.findByShiftIdOrderByIdDesc(only.id());

        ShiftCloseRequest request = new ShiftCloseRequest();
        request.setOperatorName("陈骁");
        request.setOperatorRole(EquestrianDict.ROLE_MANAGER);
        request.setExpectedVersion(only.itemVersion());
        request.setDelays(items.stream().map(i -> {
            ShiftCloseRequest.DelayItem delay = new ShiftCloseRequest.DelayItem();
            delay.setItemId(i.getId());
            delay.setReason("想延期");
            return delay;
        }).toList());
        BizException ex = assertThrows(BizException.class, () -> handoverService.close(only.id(), request));
        assertTrue(ex.getMessage().contains("还没有建立下一班"));
    }

    @Test
    void onlySourceLeaderCanCloseShift() {
        seedBasics();
        ShiftView shift = handoverService.createShift(shiftRequest("陈骁", "req-open-15"));
        ShiftCloseRequest request = new ShiftCloseRequest();
        request.setOperatorName("赵崇");
        request.setOperatorRole(EquestrianDict.ROLE_MANAGER);
        request.setExpectedVersion(shift.itemVersion());
        BizException ex = assertThrows(BizException.class, () -> handoverService.close(shift.id(), request));
        assertTrue(ex.getMessage().contains("只有本班负责人"));
    }

    @Test
    void incomingUnacceptedItemsRollForwardAcrossMultipleShifts() {
        seedBasics();
        ShiftView first = handoverService.createShift(shiftRequest("陈骁", "req-open-m1"));
        ShiftView second = handoverService.createShift(shiftRequest("赵崇", "req-open-m2"));
        // 陈骁班关班：未承接事项转给赵崇班
        List<HandoverItem> firstItems = itemRepository.findByShiftIdOrderByIdDesc(first.id());
        ShiftCloseRequest close1 = new ShiftCloseRequest();
        close1.setOperatorName("陈骁");
        close1.setOperatorRole(EquestrianDict.ROLE_MANAGER);
        close1.setExpectedVersion(first.itemVersion());
        close1.setDelays(firstItems.stream().map(i -> {
            ShiftCloseRequest.DelayItem delay = new ShiftCloseRequest.DelayItem();
            delay.setItemId(i.getId());
            delay.setReason("第一班顺延：" + i.getItemNo());
            return delay;
        }).toList());
        handoverService.close(first.id(), close1);

        // 第三班建立
        ShiftView third = handoverService.createShift(shiftRequest("林婉", "req-open-m3"));

        // 赵崇班关班预检：本班未承接事项 + 上一班转入未承接来项 都要逐项列出
        List<CloseBlockerView> blockers = handoverService.closeBlockers(second.id());
        assertTrue(blockers.stream().anyMatch(CloseBlockerView::incoming),
                "上一班转入未承接的来项必须出现在关班拦截里");
        long incomingCount = blockers.stream().filter(CloseBlockerView::incoming).count();
        assertEquals(firstItems.size(), incomingCount);

        // 赵崇班关班：所有未承接（含来项）逐项延期后继续顺延到林婉班
        ShiftCloseRequest close2 = new ShiftCloseRequest();
        close2.setOperatorName("赵崇");
        close2.setOperatorRole(EquestrianDict.ROLE_MANAGER);
        close2.setExpectedVersion(shiftRepository.findById(second.id()).orElseThrow().getItemVersion());
        close2.setDelays(blockers.stream().map(b -> {
            ShiftCloseRequest.DelayItem delay = new ShiftCloseRequest.DelayItem();
            delay.setItemId(b.itemId());
            delay.setReason("第二班继续顺延：" + b.itemNo());
            return delay;
        }).toList());
        handoverService.close(second.id(), close2);

        // 第一班产生的事项最终指向第三班，且仍是待承接
        for (HandoverItem item : itemRepository.findByShiftIdOrderByIdDesc(first.id())) {
            assertEquals(third.id(), item.getTargetShiftId());
            assertEquals(EquestrianDict.ITEM_PENDING, item.getStatus());
        }
        // 第三班首页：第一班 + 第二班的未承接事项全部在列，不因两次结班消失
        ShiftView board = handoverService.activeBoard();
        assertEquals(third.id(), board.id());
        assertEquals(firstItems.size()
                + itemRepository.countByShiftIdAndStatus(second.id(), EquestrianDict.ITEM_PENDING),
                board.incomingItems().size());
    }

    // ---------------- 并发版本：旧页面提交过期内容 409 ----------------

    @Test
    void staleItemEditReturnsConflict() {
        seedBasics();
        ShiftView shift = handoverService.createShift(shiftRequest("陈骁", "req-open-16"));
        HandoverItem item = itemRepository.findByShiftIdOrderByIdDesc(shift.id()).get(0);

        // 别人先改了一次优先级（版本 0 -> 1）
        ItemUpdateRequest first = new ItemUpdateRequest();
        first.setOperatorName("陈骁");
        first.setOperatorRole(EquestrianDict.ROLE_MANAGER);
        first.setPriority(EquestrianDict.PRIORITY_HIGH);
        first.setExpectedVersion(0L);
        handoverService.updateItem(item.getId(), first);

        // 旧页面仍拿 v0 提交
        ItemUpdateRequest stale = new ItemUpdateRequest();
        stale.setOperatorName("陈骁");
        stale.setOperatorRole(EquestrianDict.ROLE_MANAGER);
        stale.setPriority(EquestrianDict.PRIORITY_LOW);
        stale.setExpectedVersion(0L);
        ConflictException ex = assertThrows(ConflictException.class,
                () -> handoverService.updateItem(item.getId(), stale));
        assertTrue(ex.getMessage().contains("数据已变化"));
    }

    @Test
    void staleShiftCloseReturnsConflict() {
        seedBasics();
        ShiftView first = handoverService.createShift(shiftRequest("陈骁", "req-open-17"));
        handoverService.createShift(shiftRequest("赵崇", "req-open-18"));
        long oldVersion = first.itemVersion();

        // 清单发生过变化（分派一项），班次版本 +1
        HandoverItem item = itemRepository.findByShiftIdOrderByIdDesc(first.id()).get(0);
        ItemDispatchRequest dispatch = new ItemDispatchRequest();
        dispatch.setOperatorName("陈骁");
        dispatch.setOperatorRole(EquestrianDict.ROLE_MANAGER);
        dispatch.setExpectedVersion(0L);
        handoverService.dispatch(item.getId(), dispatch);

        ShiftCloseRequest request = new ShiftCloseRequest();
        request.setOperatorName("陈骁");
        request.setOperatorRole(EquestrianDict.ROLE_MANAGER);
        request.setExpectedVersion(oldVersion);
        assertThrows(ConflictException.class, () -> handoverService.close(first.id(), request));
    }

    // ---------------- 手工事项 ----------------

    @Test
    void managerCanAddManualItemAndHistoryFiltersWork() {
        seedBasics();
        ShiftView shift = handoverService.createShift(shiftRequest("陈骁", "req-open-19"));

        ItemCreateRequest create = new ItemCreateRequest();
        create.setOperatorName("陈骁");
        create.setOperatorRole(EquestrianDict.ROLE_MANAGER);
        create.setTitle("清点鞍具房");
        create.setDetail("下班前清点");
        create.setPriority(EquestrianDict.PRIORITY_LOW);
        create.setRequestId("req-manual-1");
        ItemView manual = handoverService.createItem(shift.id(), create);
        assertEquals(EquestrianDict.ITEM_TYPE_MANUAL, manual.itemType());

        // 普通教练不能手工建项
        ItemCreateRequest staff = new ItemCreateRequest();
        staff.setOperatorName("林婉");
        staff.setOperatorRole(EquestrianDict.ROLE_STAFF);
        staff.setTitle("越权建项");
        assertThrows(BizException.class, () -> handoverService.createItem(shift.id(), staff));

        // 历史按负责人 + 事项状态查询
        var history = handoverService.history(null, null, "陈骁", EquestrianDict.ITEM_PENDING);
        assertTrue(history.stream().anyMatch(s -> s.id().equals(shift.id())));
        var onlyAccepted = handoverService.history(null, null, "陈骁", EquestrianDict.ITEM_ACCEPTED);
        assertTrue(onlyAccepted.isEmpty());
    }

    // ---------------- 并发：结班 / 下一班建立 / 课程变更 / 承接同时发生 ----------------

    @Test
    void concurrentShiftCreateAndSessionChanges_neverLosesAnItem() throws Exception {
        seedBasics();
        ShiftView first = handoverService.createShift(shiftRequest("陈骁", "req-open-c1"));

        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(4);
        try {
            java.util.List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
            // 线程 1：建立下一班（与课程变更并发，序号锁串行，不死锁）
            futures.add(pool.submit(() -> handoverService.createShift(shiftRequest("赵崇", "req-open-c2"))));
            // 线程 2-4：同时新建课程（日期各不同，避开教练时段冲突）
            String[] slots = {"09:00", "14:00", "16:00"};
            for (int day = 2; day <= 4; day++) {
                final int d = day;
                futures.add(pool.submit(() ->
                        sessionService.create(sessionRequest(LocalDate.now().plusDays(d), slots[d - 2]))));
            }
            for (java.util.concurrent.Future<?> future : futures) {
                future.get(30, java.util.concurrent.TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdown();
        }

        // 三场新排期的变更事项一条不丢、一条不重，全部挂在两个 OPEN 班次之一（建班前的在陈骁班）
        List<LessonSession> all = sessionRepository.findAllByOrderBySessionDateAscStartTimeAscIdAsc();
        long newChangeItems = itemRepository.findAll().stream()
                .filter(i -> EquestrianDict.CHANGE_NEW.equals(i.getChangeKind()))
                .count();
        assertEquals(3, newChangeItems);
        assertTrue(itemRepository.findAll().stream()
                .allMatch(i -> i.getShiftId().equals(first.id())
                        || shiftRepository.findById(i.getShiftId())
                                .map(DutyShift::getStatus).orElse(null)
                                .equals(EquestrianDict.SHIFT_OPEN)));

        // 结班（逐项延期，自动转派）后：陈骁班所有未承接事项都挂在赵崇班
        List<HandoverItem> firstItems = itemRepository.findByShiftIdOrderByIdDesc(first.id());
        ShiftCloseRequest request = new ShiftCloseRequest();
        request.setOperatorName("陈骁");
        request.setOperatorRole(EquestrianDict.ROLE_MANAGER);
        request.setExpectedVersion(
                shiftRepository.findById(first.id()).orElseThrow().getItemVersion());
        request.setDelays(firstItems.stream()
                .filter(i -> EquestrianDict.ITEM_PENDING.equals(i.getStatus()))
                .map(i -> {
                    ShiftCloseRequest.DelayItem delay = new ShiftCloseRequest.DelayItem();
                    delay.setItemId(i.getId());
                    delay.setReason("并发场景顺延：" + i.getItemNo());
                    return delay;
                }).toList());
        ShiftView closed = handoverService.close(first.id(), request);
        assertEquals(EquestrianDict.SHIFT_CLOSED, closed.status());

        itemRepository.findByShiftIdOrderByIdDesc(first.id()).stream()
                .filter(i -> EquestrianDict.ITEM_PENDING.equals(i.getStatus()))
                .forEach(i -> assertNotNull(i.getTargetShiftId(), "未承接事项必须已转派，不能因结班丢失"));
    }

    @Test
    void concurrentAcceptOfSameItem_createsExactlyOneAcceptance() throws Exception {
        seedBasics();
        ShiftView first = handoverService.createShift(shiftRequest("陈骁", "req-open-c3"));
        ShiftView second = handoverService.createShift(shiftRequest("赵崇", "req-open-c4"));
        HandoverItem item = itemRepository.findByShiftIdOrderByIdDesc(first.id()).get(0);

        ItemDispatchRequest dispatch = new ItemDispatchRequest();
        dispatch.setOperatorName("陈骁");
        dispatch.setOperatorRole(EquestrianDict.ROLE_MANAGER);
        dispatch.setExpectedVersion(0L);
        handoverService.dispatch(item.getId(), dispatch);

        // 5 个线程同时确认承接同一事项（都带不同 requestId 模拟双击 + 重试）
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(5);
        java.util.List<java.util.concurrent.Future<?>> futures = new java.util.ArrayList<>();
        java.util.concurrent.atomic.AtomicInteger success = new java.util.concurrent.atomic.AtomicInteger();
        try {
            for (int i = 0; i < 5; i++) {
                final int index = i;
                futures.add(pool.submit(() -> {
                    ItemAcceptRequest accept = new ItemAcceptRequest();
                    accept.setOperatorName("赵崇");
                    accept.setOperatorRole(EquestrianDict.ROLE_MANAGER);
                    accept.setExpectedVersion(1L);
                    accept.setRequestId("req-concurrent-accept-" + index);
                    handoverService.accept(item.getId(), accept);
                    success.incrementAndGet();
                }));
            }
            for (java.util.concurrent.Future<?> future : futures) {
                try {
                    future.get(30, java.util.concurrent.TimeUnit.SECONDS);
                } catch (java.util.concurrent.ExecutionException expected) {
                    // 只有一个线程成功，其余撞已承接终态或版本冲突
                }
            }
        } finally {
            pool.shutdown();
        }

        assertEquals(1, success.get(), "并发确认只能有一个成功");
        assertTrue(acceptanceRepository.findByItemId(item.getId()).isPresent());
        assertEquals(1, acceptanceRepository.count());
    }

    @Test
    void newChangeAfterAcceptanceCreatesFreshPendingItem() {
        seedBasics();
        ShiftView first = handoverService.createShift(shiftRequest("陈骁", "req-open-a1"));
        ShiftView second = handoverService.createShift(shiftRequest("赵崇", "req-open-a2"));
        // 一场未来课进入赵崇班
        var created = sessionService.create(sessionRequest(LocalDate.now().plusDays(2), "09:00"));
        HandoverItem item = itemRepository.findByRefTypeAndRefId(
                EquestrianDict.REF_SESSION_CHANGE, created.id()).get(0);
        // 第三班建立、分派、承接
        ShiftView third = handoverService.createShift(shiftRequest("林婉", "req-open-a3"));
        ItemDispatchRequest dispatch = new ItemDispatchRequest();
        dispatch.setOperatorName("赵崇");
        dispatch.setOperatorRole(EquestrianDict.ROLE_MANAGER);
        dispatch.setExpectedVersion(0L);
        handoverService.dispatch(item.getId(), dispatch);
        ItemAcceptRequest accept = new ItemAcceptRequest();
        accept.setOperatorName("林婉");
        accept.setOperatorRole(EquestrianDict.ROLE_MANAGER);
        accept.setExpectedVersion(1L);
        accept.setRequestId("req-open-a-accept");
        handoverService.accept(item.getId(), accept);

        // 承接后该排期被取消：必须能生成一条全新的未承接事项（条件唯一键不能挡住）
        sessionService.cancel(created.id(), "赵崇");
        List<HandoverItem> all = itemRepository.findByRefTypeAndRefId(
                EquestrianDict.REF_SESSION_CHANGE, created.id());
        assertEquals(2, all.size());
        HandoverItem canceled = all.stream()
                .filter(i -> EquestrianDict.ITEM_PENDING.equals(i.getStatus())).findFirst().orElseThrow();
        assertEquals(EquestrianDict.CHANGE_CANCELED, canceled.getChangeKind());
        // 旧事项保持已承接，不能被改回未处理
        assertTrue(all.stream().anyMatch(i -> EquestrianDict.ITEM_ACCEPTED.equals(i.getStatus())));
    }

    // ---------------- 课程详情归属与承接记录 ----------------

    @Test
    void sessionDetailShowsShiftAndLatestAcceptance() {
        seedBasics();
        ShiftView first = handoverService.createShift(shiftRequest("陈骁", "req-open-20"));
        ShiftView second = handoverService.createShift(shiftRequest("赵崇", "req-open-21"));
        // 新增一场未来课 -> NEW 事项进入「当前最新 OPEN 班次」= 赵崇班
        var created = sessionService.create(sessionRequest(LocalDate.now().plusDays(2), "15:00"));

        var summaryBefore = handoverService.sessionHandoverSummary(created.id());
        assertNotNull(summaryBefore);
        assertEquals(second.shiftNo(), summaryBefore.shiftNo());
        assertNull(summaryBefore.acceptedBy());

        // 下一班（第三班）建立后，赵崇班把该事项分派出去，新班次负责人逐项承接
        ShiftView third = handoverService.createShift(shiftRequest("林婉", "req-open-22"));
        HandoverItem newItem = itemRepository.findByRefTypeAndRefId(
                EquestrianDict.REF_SESSION_CHANGE, created.id()).get(0);
        ItemDispatchRequest dispatch = new ItemDispatchRequest();
        dispatch.setOperatorName("赵崇");
        dispatch.setOperatorRole(EquestrianDict.ROLE_MANAGER);
        dispatch.setExpectedVersion(0L);
        handoverService.dispatch(newItem.getId(), dispatch);

        ItemAcceptRequest accept = new ItemAcceptRequest();
        accept.setOperatorName("林婉");
        accept.setOperatorRole(EquestrianDict.ROLE_MANAGER);
        accept.setExpectedVersion(1L);
        accept.setRequestId("req-accept-session");
        handoverService.accept(newItem.getId(), accept);

        var summaryAfter = handoverService.sessionHandoverSummary(created.id());
        assertEquals(EquestrianDict.ITEM_ACCEPTED, summaryAfter.itemStatus());
        assertEquals("林婉", summaryAfter.acceptedBy());
        assertEquals(third.shiftNo(), summaryAfter.acceptedShiftNo());
        // 来源班次仍是赵崇班（课程属于产生它的班）
        assertEquals(second.shiftNo(), summaryAfter.shiftNo());
        assertNotNull(first);
    }
}
