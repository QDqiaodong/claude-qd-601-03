package com.equestrian.club.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.equestrian.club.common.BizException;
import com.equestrian.club.dict.EquestrianDict;
import com.equestrian.club.domain.Horse;
import com.equestrian.club.domain.HorseRepository;
import com.equestrian.club.domain.Stall;
import com.equestrian.club.domain.StallRepository;
import com.equestrian.club.dto.OccupyRequest;
import com.equestrian.club.dto.StallRequest;
import com.equestrian.club.dto.view.StallBoardView;
import com.equestrian.club.dto.view.StallView;

/**
 * 马房与栏位模块：栏位编号唯一 / 一个栏位只能放一匹马 / 一匹马只能占一个栏位 / 维护中禁止入栏。
 *
 * 读取分两层：
 *  - {@link #board()} 给看板用，单个只读事务内同时算出顶部汇总、马房分组、栏位明细，
 *    三者来自同一批 stall 行（同一轮快照），前端整包替换即可保证三处数字不打架；
 *  - 明细映射对历史脏数据（占用马匹档案缺失、面积未登记、马房名为空）保持容错，
 *    不让一条坏引用把整个看板打成 500、再被前端误判成「全部为空」。
 */
@Service
public class StallService {

    /** 马房名为空 / 全空白时的兜底分组名，保证每个栏位都有分组归属 */
    public static final String UNASSIGNED_BARN = "未分组马房";

    private final StallRepository stallRepository;
    private final HorseService horseService;
    private final HorseRepository horseRepository;

    public StallService(StallRepository stallRepository,
            HorseService horseService,
            HorseRepository horseRepository) {
        this.stallRepository = stallRepository;
        this.horseService = horseService;
        this.horseRepository = horseRepository;
    }

    /**
     * 看板单轮读取：汇总 / 分组 / 明细在同一事务、同一批数据上计算。
     * 整份结果一次性返回，调用方要么拿到完整一致的一轮，要么请求失败（由全局异常处理提示），
     * 不存在「明细更新了一半、汇总还是旧值」的中间态。
     */
    @Transactional(readOnly = true)
    public StallBoardView board() {
        List<Stall> stalls = stallRepository.findAllByOrderByIdAsc();

        // 一次性把本批栏位引用到的马查全，避免逐栏位查库；用 Map 对脏引用做容错。
        List<Long> horseIds = stalls.stream()
                .map(Stall::getHorseId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<Long, Horse> horseMap = horseIds.isEmpty()
                ? Map.of()
                : horseRepository.findAllById(horseIds).stream()
                        .collect(Collectors.toMap(Horse::getId, Function.identity()));

        List<StallView> views = new ArrayList<>(stalls.size());
        Map<String, List<StallView>> byBarn = new LinkedHashMap<>();
        long idle = 0;
        long occupied = 0;
        long maintenance = 0;
        long unknown = 0;

        for (Stall stall : stalls) {
            StallView view = toBoardView(stall, horseMap);
            views.add(view);

            String barn = normalizeBarn(view.barnName());
            byBarn.computeIfAbsent(barn, key -> new ArrayList<>()).add(view);

            // 占用数只认真正处于 OCCUPIED 的栏位；与马房分组的「已用」同一口径
            if (EquestrianDict.STALL_IDLE.equals(view.status())) {
                idle++;
            } else if (EquestrianDict.STALL_OCCUPIED.equals(view.status())) {
                occupied++;
            } else if (EquestrianDict.STALL_MAINTENANCE.equals(view.status())) {
                maintenance++;
            } else {
                // 字典外的异常状态：计入总数但单独归类，不能被静默塞进空闲 / 维护
                unknown++;
            }
        }

        List<StallBoardView.Barn> barns = new ArrayList<>(byBarn.size());
        for (Map.Entry<String, List<StallView>> entry : byBarn.entrySet()) {
            long barnOccupied = entry.getValue().stream()
                    .filter(item -> EquestrianDict.STALL_OCCUPIED.equals(item.status()))
                    .count();
            barns.add(new StallBoardView.Barn(
                    entry.getKey(), entry.getValue().size(), barnOccupied, entry.getValue()));
        }

        StallBoardView.Summary summary = new StallBoardView.Summary(
                views.size(), idle, occupied, maintenance, unknown);
        return new StallBoardView(java.time.LocalDateTime.now(), summary, barns, views);
    }

    @Transactional(readOnly = true)
    public List<StallView> list() {
        return board().stalls();
    }

    @Transactional(readOnly = true)
    public StallView detail(Long id) {
        return toView(require(id));
    }

    @Transactional
    public StallView create(StallRequest request) {
        if (request.getStallNo() == null || request.getStallNo().isBlank()) {
            throw new BizException("栏位编号不能为空");
        }
        String stallNo = request.getStallNo().trim();
        if (stallRepository.existsByStallNo(stallNo)) {
            throw new BizException("栏位编号「" + stallNo + "」已存在，编号必须唯一");
        }
        if (request.getBarnName() == null || request.getBarnName().isBlank()) {
            throw new BizException("所属马房不能为空");
        }

        Stall stall = new Stall();
        stall.setStallNo(stallNo);
        stall.setBarnName(request.getBarnName().trim());
        stall.setAreaSqm(request.getAreaSqm());

        // 默认值放在 service 的 create 里，实体上不写默认值
        String status = request.getStatus() == null ? EquestrianDict.STALL_IDLE : request.getStatus();
        if (!EquestrianDict.isValidStallStatus(status)) {
            throw new BizException("栏位状态取值不合法：" + status + "，只支持 IDLE / OCCUPIED / MAINTENANCE");
        }
        if (EquestrianDict.STALL_OCCUPIED.equals(status)) {
            throw new BizException("新建栏位不能直接置为「占用」，请先用入栏操作绑定马匹");
        }
        stall.setStatus(status);
        stall.setHorseId(null);

        return toView(stallRepository.save(stall));
    }

    @Transactional
    public StallView update(Long id, StallRequest request) {
        Stall stall = require(id);
        if (request.getStallNo() != null && !request.getStallNo().isBlank()) {
            String stallNo = request.getStallNo().trim();
            if (!stallNo.equals(stall.getStallNo())) {
                if (stallRepository.existsByStallNo(stallNo)) {
                    throw new BizException("栏位编号「" + stallNo + "」已存在，编号必须唯一");
                }
                stall.setStallNo(stallNo);
            }
        }
        if (request.getBarnName() != null && !request.getBarnName().isBlank()) {
            stall.setBarnName(request.getBarnName().trim());
        }
        if (request.getAreaSqm() != null) {
            stall.setAreaSqm(request.getAreaSqm());
        }
        if (request.getStatus() != null) {
            throw new BizException("栏位状态由入栏 / 出栏 / 维护操作驱动，不通过编辑直接修改");
        }
        return toView(stallRepository.save(stall));
    }

    /**
     * 入栏：维护中的栏位不能入栏，已有马的栏位不能再放，一匹马也只能占一个栏位。
     */
    @Transactional
    public StallView occupy(Long id, OccupyRequest request) {
        Stall stall = require(id);

        // 可空外键先判 null，再查库，避免甩给用户一个非人话的数据库报错
        Long horseId = request == null ? null : request.getHorseId();
        if (horseId == null) {
            throw new BizException("入栏失败：请先选择要入栏的马匹");
        }

        if (EquestrianDict.STALL_MAINTENANCE.equals(stall.getStatus())) {
            throw new BizException("栏位「" + stall.getStallNo() + "」正在维护中，禁止入栏");
        }
        if (stall.getHorseId() != null) {
            Horse current = horseService.require(stall.getHorseId());
            throw new BizException("栏位「" + stall.getStallNo() + "」已放着马匹「" + current.getName()
                    + "」，一个栏位只能放一匹马");
        }

        Horse horse = horseService.require(horseId);
        stallRepository.findByHorseId(horseId).ifPresent(other -> {
            if (!other.getId().equals(stall.getId())) {
                throw new BizException("马匹「" + horse.getName() + "」已在栏位「" + other.getStallNo()
                        + "」中，一匹马只能占用一个栏位");
            }
        });

        stall.setHorseId(horse.getId());
        stall.setStatus(EquestrianDict.STALL_OCCUPIED);
        return toView(stallRepository.save(stall));
    }

    /** 出栏：清空占用并把栏位置回空闲 */
    @Transactional
    public StallView release(Long id) {
        Stall stall = require(id);
        if (stall.getHorseId() == null) {
            throw new BizException("栏位「" + stall.getStallNo() + "」当前没有马匹，无需出栏");
        }
        stall.setHorseId(null);
        if (!EquestrianDict.STALL_MAINTENANCE.equals(stall.getStatus())) {
            stall.setStatus(EquestrianDict.STALL_IDLE);
        }
        return toView(stallRepository.save(stall));
    }

    /** 转入维护：栏位里还有马就必须先出栏 */
    @Transactional
    public StallView maintenance(Long id) {
        Stall stall = require(id);
        if (EquestrianDict.STALL_MAINTENANCE.equals(stall.getStatus())) {
            throw new BizException("栏位「" + stall.getStallNo() + "」已经是维护中状态");
        }
        if (stall.getHorseId() != null) {
            Horse horse = horseService.require(stall.getHorseId());
            throw new BizException("栏位「" + stall.getStallNo() + "」仍放着马匹「" + horse.getName()
                    + "」，请先出栏再转入维护");
        }
        stall.setStatus(EquestrianDict.STALL_MAINTENANCE);
        return toView(stallRepository.save(stall));
    }

    /** 维护完成，恢复空闲 */
    @Transactional
    public StallView restore(Long id) {
        Stall stall = require(id);
        if (!EquestrianDict.STALL_MAINTENANCE.equals(stall.getStatus())) {
            throw new BizException("栏位「" + stall.getStallNo() + "」不在维护中，无需恢复");
        }
        stall.setStatus(EquestrianDict.STALL_IDLE);
        return toView(stallRepository.save(stall));
    }

    private Stall require(Long id) {
        if (id == null) {
            throw new BizException("请先选择栏位");
        }
        return stallRepository.findById(id)
                .orElseThrow(() -> new BizException("栏位不存在，编号：" + id));
    }

    /** 写操作回执：马匹是刚校验过的，引用必须存在，沿用严格校验口径 */
    private StallView toView(Stall stall) {
        String horseNo = null;
        String horseName = null;
        if (stall.getHorseId() != null) {
            Horse horse = horseService.require(stall.getHorseId());
            horseNo = horse.getHorseNo();
            horseName = horse.getName();
        }
        return new StallView(
                stall.getId(),
                stall.getStallNo(),
                stall.getBarnName(),
                stall.getAreaSqm(),
                stall.getStatus(),
                EquestrianDict.stallStatusName(stall.getStatus()),
                stall.getHorseId(),
                horseNo,
                horseName,
                false);
    }

    /**
     * 看板明细映射：对读路径上的合法 / 历史脏数据给稳定显示规则，不抛异常。
     *  - horse_id 非空但马匹档案缺失：horseMissing=true，卡片明确提示，仍按占用计数；
     *  - 面积为空：areaSqm=null，卡片显示「面积未登记」；
     *  - 马房名为空：归一到 {@link #UNASSIGNED_BARN}，不产生 undefined 分组；
     *  - 状态不在字典：原样返回 code，statusName 回退为 code，由汇总归入 unknown。
     */
    private StallView toBoardView(Stall stall, Map<Long, Horse> horseMap) {
        Long horseId = stall.getHorseId();
        String horseNo = null;
        String horseName = null;
        boolean horseMissing = false;
        if (horseId != null) {
            Horse horse = horseMap.get(horseId);
            if (horse != null) {
                horseNo = horse.getHorseNo();
                horseName = horse.getName();
            } else {
                horseMissing = true;
            }
        }
        String status = stall.getStatus();
        String statusName = EquestrianDict.isValidStallStatus(status)
                ? EquestrianDict.stallStatusName(status)
                : Optional.ofNullable(status).orElse("UNKNOWN");
        return new StallView(
                stall.getId(),
                stall.getStallNo(),
                normalizeBarn(stall.getBarnName()),
                stall.getAreaSqm(),
                status,
                statusName,
                horseId,
                horseNo,
                horseName,
                horseMissing);
    }

    private String normalizeBarn(String barnName) {
        if (barnName == null || barnName.isBlank()) {
            return UNASSIGNED_BARN;
        }
        return barnName.trim();
    }
}
