package com.equestrian.club;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.equestrian.club.dict.EquestrianDict;
import com.equestrian.club.domain.Horse;
import com.equestrian.club.domain.HorseRepository;
import com.equestrian.club.domain.Stall;
import com.equestrian.club.domain.StallRepository;
import com.equestrian.club.dto.OccupyRequest;
import com.equestrian.club.dto.view.StallBoardView;
import com.equestrian.club.dto.view.StallView;
import com.equestrian.club.service.StallService;

/**
 * 栏位看板「读取与展示一致性」集成测试：
 * 顶部汇总、马房分组、栏位卡片必须来自同一轮 board() 快照；
 * 覆盖操作后快照联动、缺失马匹 / 空面积 / 空马房等脏数据容错、重复刷新不累加。
 */
@SpringBootTest
class StallBoardIntegrationTest {

    @Autowired
    private StallService stallService;
    @Autowired
    private StallRepository stallRepository;
    @Autowired
    private HorseRepository horseRepository;

    @BeforeEach
    void cleanUp() {
        stallRepository.deleteAllInBatch();
        horseRepository.deleteAllInBatch();
    }

    private Horse horse(String no, String name) {
        Horse horse = new Horse();
        horse.setHorseNo(no);
        horse.setName(name);
        horse.setStatus(EquestrianDict.HORSE_ACTIVE);
        horse.setRideLevel("BEGINNER_SAFE");
        horse.setHealthVersion(0L);
        return horseRepository.save(horse);
    }

    private Stall stall(String no, String barn, String status, Long horseId) {
        Stall stall = new Stall();
        stall.setStallNo(no);
        stall.setBarnName(barn);
        stall.setStatus(status);
        stall.setHorseId(horseId);
        return stallRepository.save(stall);
    }

    /** 三轮数据互相印证：汇总、分组、明细来自同一批 stalls */
    private void assertSnapshotInternallyConsistent(StallBoardView board) {
        List<StallView> stalls = board.stalls();
        long idle = stalls.stream().filter(s -> EquestrianDict.STALL_IDLE.equals(s.status())).count();
        long occupied = stalls.stream().filter(s -> EquestrianDict.STALL_OCCUPIED.equals(s.status())).count();
        long maintenance = stalls.stream().filter(s -> EquestrianDict.STALL_MAINTENANCE.equals(s.status())).count();

        assertEquals(stalls.size(), board.summary().total());
        assertEquals(idle, board.summary().idle());
        assertEquals(occupied, board.summary().occupied());
        assertEquals(maintenance, board.summary().maintenance());

        // 分组卡片数 / 已用数加总等于顶部数字，每组内部也自洽
        assertEquals(board.summary().total(), board.barns().stream().mapToLong(StallBoardView.Barn::total).sum());
        assertEquals(board.summary().occupied(),
                board.barns().stream().mapToLong(StallBoardView.Barn::occupied).sum());
        for (StallBoardView.Barn barn : board.barns()) {
            assertEquals(barn.stalls().size(), barn.total());
            assertEquals(barn.stalls().stream().filter(s -> EquestrianDict.STALL_OCCUPIED.equals(s.status())).count(),
                    barn.occupied());
            assertTrue(barn.stalls().stream().allMatch(s -> s.barnName().equals(barn.barnName())));
        }

        // 明细不允许重复 id
        long distinctIds = stalls.stream().map(StallView::id).distinct().count();
        assertEquals(stalls.size(), distinctIds);
        assertNotNull(board.generatedAt());
    }

    @Test
    void emptyBoard_isAValidRoundWithZeroSummaryAndNoBarns() {
        StallBoardView board = stallService.board();
        assertEquals(0, board.summary().total());
        assertTrue(board.stalls().isEmpty());
        assertTrue(board.barns().isEmpty());
    }

    @Test
    void boardCountsMatchStallsAndOperationsMoveAllThreePartsTogether() {
        Horse h1 = horse("H-T10", "追风");
        Horse h2 = horse("H-T11", "踏雪");
        stall("STM-T10", "A 区马房", EquestrianDict.STALL_IDLE, null);
        stall("STM-T11", "A 区马房", EquestrianDict.STALL_IDLE, null);
        stall("STM-T12", "B 区马房", EquestrianDict.STALL_MAINTENANCE, null);
        Stall occupied = stall("STM-T13", "B 区马房", EquestrianDict.STALL_IDLE, null);

        StallBoardView initial = stallService.board();
        assertSnapshotInternallyConsistent(initial);
        assertEquals(4, initial.summary().total());
        assertEquals(3, initial.summary().idle());
        assertEquals(0, initial.summary().occupied());
        assertEquals(1, initial.summary().maintenance());

        // 入栏：新一轮快照里顶部占用、B 区已用、卡片马匹信息必须同时变化
        OccupyRequest occupy = new OccupyRequest();
        occupy.setHorseId(h1.getId());
        stallService.occupy(occupied.getId(), occupy);
        StallBoardView afterOccupy = stallService.board();
        assertSnapshotInternallyConsistent(afterOccupy);
        assertEquals(1, afterOccupy.summary().occupied());
        assertEquals(2, afterOccupy.summary().idle());
        StallView card = afterOccupy.stalls().stream().filter(s -> s.id().equals(occupied.getId())).findFirst().orElseThrow();
        assertEquals(EquestrianDict.STALL_OCCUPIED, card.status());
        assertEquals(h1.getId(), card.horseId());
        assertEquals("追风", card.horseName());
        assertEquals("H-T10", card.horseNo());
        assertFalse(card.horseMissing());
        StallBoardView.Barn barnB = afterOccupy.barns().stream()
                .filter(b -> b.barnName().equals("B 区马房")).findFirst().orElseThrow();
        assertEquals(1, barnB.occupied());

        // 出栏：旧马匹必须同时从计数与卡片消失，不能残留在展示链路里
        stallService.release(occupied.getId());
        StallBoardView afterRelease = stallService.board();
        assertSnapshotInternallyConsistent(afterRelease);
        assertEquals(0, afterRelease.summary().occupied());
        assertEquals(3, afterRelease.summary().idle());
        StallView released = afterRelease.stalls().stream().filter(s -> s.id().equals(occupied.getId())).findFirst().orElseThrow();
        assertEquals(EquestrianDict.STALL_IDLE, released.status());
        assertNull(released.horseId());
        assertNull(released.horseName());

        // 转维护：维护数量与卡片状态（只剩「维护完成」按钮所依据的状态）同轮切换
        stall("STM-T14", "C 区放牧场", EquestrianDict.STALL_IDLE, null);
        Stall idle = stallRepository.findByStallNo("STM-T10").orElseThrow();
        stallService.maintenance(idle.getId());
        StallBoardView afterMaintenance = stallService.board();
        assertSnapshotInternallyConsistent(afterMaintenance);
        assertEquals(2, afterMaintenance.summary().maintenance());
        assertEquals(3, afterMaintenance.summary().idle());
        StallView maintaining = afterMaintenance.stalls().stream()
                .filter(s -> s.id().equals(idle.getId())).findFirst().orElseThrow();
        assertEquals(EquestrianDict.STALL_MAINTENANCE, maintaining.status());
        assertNull(maintaining.horseId());

        // 维护完成
        stallService.restore(idle.getId());
        StallBoardView afterRestore = stallService.board();
        assertSnapshotInternallyConsistent(afterRestore);
        assertEquals(1, afterRestore.summary().maintenance());
        assertEquals(4, afterRestore.summary().idle());

        // h2 从未入栏，避免未使用告警的同时保证数据干净
        assertNotNull(h2.getId());
    }

    @Test
    void repeatedRefreshDoesNotAccumulateAndOrderingDoesNotDuplicate() {
        Horse horse = horse("H-T20", "灰姑娘");
        stall("STM-T20", "A 区马房", EquestrianDict.STALL_OCCUPIED, horse.getId());
        stall("STM-T21", "A 区马房", EquestrianDict.STALL_IDLE, null);

        StallBoardView first = stallService.board();
        StallBoardView second = stallService.board();
        StallBoardView third = stallService.board();

        for (StallBoardView board : List.of(first, second, third)) {
            assertSnapshotInternallyConsistent(board);
            assertEquals(2, board.summary().total());
            assertEquals(1, board.summary().occupied());
        }
        // 每轮都是独立的完整快照，刷新不追加、不累加
        assertEquals(first.stalls().size(), third.stalls().size());
    }

    @Test
    void missingHorseEmptyAreaAndBlankBarnHaveStableDisplayRules() {
        Horse horse = horse("H-T30", "黑旋风");
        // 占用中的栏位引用了已不存在的马匹 id（历史脏数据）
        stall("STM-T30", "A 区马房", EquestrianDict.STALL_OCCUPIED, 999999L);
        // 空面积 + 空马房名：合法 / 可兼容数据，需要稳定显示规则
        Stall blank = new Stall();
        blank.setStallNo("STM-T31");
        blank.setBarnName("   ");
        blank.setAreaSqm(null);
        blank.setStatus(EquestrianDict.STALL_IDLE);
        stallRepository.save(blank);
        // 正常占用作为对照
        stall("STM-T32", "B 区马房", EquestrianDict.STALL_OCCUPIED, horse.getId());

        StallBoardView board = stallService.board();
        assertSnapshotInternallyConsistent(board);

        // 缺马档案不炸整个看板，且仍按占用计数（不能当成空栏少算）
        StallView missing = board.stalls().stream().filter(s -> s.stallNo().equals("STM-T30")).findFirst().orElseThrow();
        assertTrue(missing.horseMissing());
        assertEquals(999999L, missing.horseId());
        assertNull(missing.horseName());
        assertEquals(EquestrianDict.STALL_OCCUPIED, missing.status());
        assertEquals(2, board.summary().occupied());

        // 空面积原样为 null；空白马房名归入「未分组马房」
        StallView unassigned = board.stalls().stream().filter(s -> s.stallNo().equals("STM-T31")).findFirst().orElseThrow();
        assertNull(unassigned.areaSqm());
        assertEquals(StallService.UNASSIGNED_BARN, unassigned.barnName());
        assertTrue(board.barns().stream().anyMatch(b -> b.barnName().equals(StallService.UNASSIGNED_BARN)));

        // 正常占用的卡片信息完整，未被缺马引用污染
        StallView healthy = board.stalls().stream().filter(s -> s.stallNo().equals("STM-T32")).findFirst().orElseThrow();
        assertFalse(healthy.horseMissing());
        assertEquals("黑旋风", healthy.horseName());

        // 明细可追溯：id -> 视图唯一，按 id 索引后计数与汇总一致
        Map<Long, StallView> byId = board.stalls().stream()
                .collect(Collectors.toMap(StallView::id, Function.identity()));
        assertEquals(board.summary().total(), byId.size());
    }
}
