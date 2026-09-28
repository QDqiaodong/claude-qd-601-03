-- ============================================================================
-- 马术俱乐部管理系统 —— 建表 + 种子数据
-- 库名 equestrian_club，字符集 utf8mb4。
-- 本文件同时被 mysql 镜像在构建期导入（mysql/Dockerfile 会 COPY schema.sql），
-- 因此 backend/src/main/resources/schema.sql 与 mysql/schema.sql 必须保持一致。
--
-- 种子数据的设计口径：下面每一条业务规则都能用现成数据直接触发，不存在永远排不到的规则。
--   马匹编号唯一 / 在役状态机（退役为终态） / 退役或休养马禁止排课
--   栏位编号唯一 / 一个栏位只能放一匹马 / 一匹马只能占一个栏位 / 维护中禁止入栏
--   课程容量封顶 / 同一教练同一时段不重叠 / 教练休假不能排期
--   未通过初级考核不能约进阶课 / 会员卡次数与余额不足不能约 / 
--   骑乘记录必须关联已排期课程 / 同一会员同一天同一时段不能重复约
--   健康事件闭环：高风险立即休养并冻结排课 / 事件链四态流转 / 复查合格且无更新事件方可复训放行
-- ============================================================================

CREATE DATABASE IF NOT EXISTS equestrian_club
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE equestrian_club;

SET NAMES utf8mb4;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS riding_record;
DROP TABLE IF EXISTS handover_item_log;
DROP TABLE IF EXISTS handover_acceptance;
DROP TABLE IF EXISTS handover_item;
DROP TABLE IF EXISTS duty_seq_lock;
DROP TABLE IF EXISTS duty_shift;
DROP TABLE IF EXISTS health_event_session;
DROP TABLE IF EXISTS health_review;
DROP TABLE IF EXISTS health_event_log;
DROP TABLE IF EXISTS health_event;
DROP TABLE IF EXISTS lesson_session;
DROP TABLE IF EXISTS lesson;
DROP TABLE IF EXISTS coach;
DROP TABLE IF EXISTS stall;
DROP TABLE IF EXISTS horse_health;
DROP TABLE IF EXISTS horse;
DROP TABLE IF EXISTS club_member;
SET FOREIGN_KEY_CHECKS = 1;

-- ---------------------------------------------------------------------------
-- 马匹主表：基础信息
-- ---------------------------------------------------------------------------
CREATE TABLE horse (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    horse_no    VARCHAR(32)  NOT NULL COMMENT '马匹编号，唯一',
    name        VARCHAR(64)  NOT NULL COMMENT '马匹名',
    breed       VARCHAR(32)           COMMENT '品种',
    gender      VARCHAR(16)           COMMENT 'MALE 公 / FEMALE 母 / GELDING 骟',
    birth_year  INT                   COMMENT '出生年份',
    status      VARCHAR(16)  NOT NULL COMMENT '在役状态机：ACTIVE 在役 / RESTING 休养 / RETIRED 退役',
    ride_level  VARCHAR(16)  NOT NULL COMMENT 'BEGINNER_SAFE / INTERMEDIATE / ADVANCED',
    -- 健康事件链版本号：该马每发生一次登记 / 流转 / 补充 / 复查 / 放行就 +1，
    -- 页面提交时回传自己看到的版本，版本对不上说明期间被别人动过，直接并发冲突
    health_version BIGINT   NOT NULL DEFAULT 0 COMMENT '健康事件链版本（乐观锁）',
    created_at  DATETIME              COMMENT '创建时间',
    updated_at  DATETIME              COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_horse_no (horse_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '马匹基础信息';

-- ---------------------------------------------------------------------------
-- 马匹副表：健康档案（@SecondaryTable 的目标表，主键即 horse.id）
-- ---------------------------------------------------------------------------
CREATE TABLE horse_health (
    horse_id        BIGINT       NOT NULL COMMENT '与 horse.id 同值',
    last_check_date DATE                  COMMENT '最近体检日期',
    vaccine_count   INT                   COMMENT '累计疫苗接种次数',
    weight_kg       DECIMAL(6, 1)         COMMENT '体重（公斤）',
    health_note     VARCHAR(255)          COMMENT '健康备注',
    PRIMARY KEY (horse_id),
    CONSTRAINT fk_horse_health_horse FOREIGN KEY (horse_id) REFERENCES horse (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '马匹健康档案（副表）';

-- ---------------------------------------------------------------------------
-- 栏位
-- ---------------------------------------------------------------------------
CREATE TABLE stall (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    stall_no   VARCHAR(32)   NOT NULL COMMENT '栏位编号，唯一',
    barn_name  VARCHAR(64)   NOT NULL COMMENT '所属马房',
    area_sqm   DECIMAL(6, 1)          COMMENT '栏位面积',
    status     VARCHAR(16)   NOT NULL COMMENT 'IDLE 空闲 / OCCUPIED 占用 / MAINTENANCE 维护中',
    horse_id   BIGINT                 COMMENT '可空外键：当前占用该栏位的马匹',
    created_at DATETIME               COMMENT '创建时间',
    updated_at DATETIME               COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_stall_no (stall_no),
    -- 一匹马只能占用一个栏位
    UNIQUE KEY uk_stall_horse (horse_id),
    CONSTRAINT fk_stall_horse FOREIGN KEY (horse_id) REFERENCES horse (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '马房栏位';

-- ---------------------------------------------------------------------------
-- 教练
-- ---------------------------------------------------------------------------
CREATE TABLE coach (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    coach_no   VARCHAR(32)  NOT NULL COMMENT '教练工号，唯一',
    name       VARCHAR(64)  NOT NULL COMMENT '教练姓名',
    level      VARCHAR(16)  NOT NULL COMMENT 'JUNIOR 初级教练 / SENIOR 高级教练 / CHIEF 总教练',
    phone      VARCHAR(32)           COMMENT '联系电话',
    status     VARCHAR(16)  NOT NULL COMMENT 'ON_DUTY 在岗 / LEAVE 休假',
    created_at DATETIME              COMMENT '创建时间',
    updated_at DATETIME              COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_coach_no (coach_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '教练';

-- ---------------------------------------------------------------------------
-- 骑乘课程（课程定义）
-- ---------------------------------------------------------------------------
CREATE TABLE lesson (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    lesson_no    VARCHAR(32)   NOT NULL COMMENT '课程编号，唯一',
    name         VARCHAR(64)   NOT NULL COMMENT '课程名称',
    category     VARCHAR(16)   NOT NULL COMMENT 'BASIC 初级课 / ADVANCED 进阶课 / PRIVATE 私教课',
    capacity     INT           NOT NULL COMMENT '课程容量上限',
    duration_min INT           NOT NULL COMMENT '单次时长（分钟）',
    price        DECIMAL(10, 2) NOT NULL COMMENT '单次标准价',
    times_cost   INT           NOT NULL COMMENT '每次预约消耗的会员卡次数',
    status       VARCHAR(16)   NOT NULL COMMENT 'ON 上架 / OFF 下架',
    created_at   DATETIME               COMMENT '创建时间',
    updated_at   DATETIME               COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_lesson_no (lesson_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '骑乘课程';

-- ---------------------------------------------------------------------------
-- 课程排期（课程的具体场次）
-- ---------------------------------------------------------------------------
CREATE TABLE lesson_session (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    lesson_id    BIGINT      NOT NULL COMMENT '课程',
    coach_id     BIGINT      NOT NULL COMMENT '执教教练',
    horse_id     BIGINT               COMMENT '可空外键：指定用马',
    session_date DATE        NOT NULL COMMENT '排期日期',
    start_time   VARCHAR(5)  NOT NULL COMMENT '开始时间 HH:mm',
    end_time     VARCHAR(5)  NOT NULL COMMENT '结束时间 HH:mm',
    capacity     INT         NOT NULL COMMENT '本场容量',
    booked_count INT         NOT NULL COMMENT '已约人数',
    status       VARCHAR(16) NOT NULL COMMENT 'SCHEDULED 已排期 / FULL 已满员 / CANCELED 已取消',
    -- 健康事件标记：马匹被高风险事件打入休养时，当时排着的未来场次只标记、不删除，
    -- 标记后该场不能再被新预约（马匹非在役），但取消排期等原有流程不受影响
    health_affected TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否被健康事件标记为受影响',
    created_at   DATETIME             COMMENT '创建时间',
    updated_at   DATETIME             COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_session_coach_date (coach_id, session_date),
    KEY idx_session_horse_date (horse_id, session_date),
    CONSTRAINT fk_session_lesson FOREIGN KEY (lesson_id) REFERENCES lesson (id),
    CONSTRAINT fk_session_coach FOREIGN KEY (coach_id) REFERENCES coach (id),
    CONSTRAINT fk_session_horse FOREIGN KEY (horse_id) REFERENCES horse (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '课程排期';

-- ---------------------------------------------------------------------------
-- 会员
-- ---------------------------------------------------------------------------
CREATE TABLE club_member (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    member_no    VARCHAR(32)   NOT NULL COMMENT '会员编号，唯一',
    name         VARCHAR(64)   NOT NULL COMMENT '会员姓名',
    phone        VARCHAR(32)            COMMENT '手机号',
    level        VARCHAR(16)   NOT NULL COMMENT 'NORMAL / SILVER / GOLD / DIAMOND',
    card_balance DECIMAL(10, 2) NOT NULL COMMENT '会员卡余额',
    card_times   INT           NOT NULL COMMENT '会员卡剩余次数',
    passed_basic TINYINT(1)    NOT NULL COMMENT '是否已通过初级考核',
    status       VARCHAR(16)   NOT NULL COMMENT 'NORMAL 正常 / FROZEN 冻结',
    created_at   DATETIME               COMMENT '创建时间',
    updated_at   DATETIME               COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_member_no (member_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '会员';

-- ---------------------------------------------------------------------------
-- 骑乘记录
-- ---------------------------------------------------------------------------
CREATE TABLE riding_record (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    member_id   BIGINT        NOT NULL COMMENT '会员',
    session_id  BIGINT        NOT NULL COMMENT '必须关联一条已排期的课程场次',
    horse_id    BIGINT                 COMMENT '可空外键：实际用马',
    record_date DATE          NOT NULL COMMENT '骑乘日期（与排期一致）',
    start_time  VARCHAR(5)    NOT NULL COMMENT '时段开始',
    end_time    VARCHAR(5)    NOT NULL COMMENT '时段结束',
    status      VARCHAR(16)   NOT NULL COMMENT 'BOOKED 已预约 / COMPLETED 已完成 / CANCELED 已取消',
    fee         DECIMAL(10, 2) NOT NULL COMMENT '实收金额（按会员等级折扣折算）',
    times_used  INT           NOT NULL COMMENT '扣减的会员卡次数',
    remark      VARCHAR(255)           COMMENT '备注',
    created_at  DATETIME               COMMENT '创建时间',
    updated_at  DATETIME               COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_record_member_slot (member_id, record_date, start_time),
    KEY idx_record_session (session_id),
    CONSTRAINT fk_record_member FOREIGN KEY (member_id) REFERENCES club_member (id),
    CONSTRAINT fk_record_session FOREIGN KEY (session_id) REFERENCES lesson_session (id),
    CONSTRAINT fk_record_horse FOREIGN KEY (horse_id) REFERENCES horse (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '骑乘记录';

-- ---------------------------------------------------------------------------
-- 健康事件：从「发生」到「观察复查」再到「复训放行」的完整闭环主表
-- 状态机：PENDING 待处理 -> OBSERVING 观察中 -> REVIEW_PENDING 待复查 -> CLOSED 已关闭
-- CLOSED 只能由负责人复训放行时系统关闭，工作人员不能手工关单（不能绕过放行）。
-- ---------------------------------------------------------------------------
CREATE TABLE health_event (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    event_no          VARCHAR(32)  NOT NULL COMMENT '事件编号，唯一，形如 HE-000007',
    horse_id          BIGINT       NOT NULL COMMENT '事件马匹',
    occurred_at       DATETIME     NOT NULL COMMENT '发生时间',
    severity          VARCHAR(16)  NOT NULL COMMENT 'HIGH 高风险 / MEDIUM 中风险 / LOW 低风险',
    symptom           VARCHAR(500) NOT NULL COMMENT '症状说明',
    treatment_advice  VARCHAR(500) NOT NULL COMMENT '处置建议',
    expected_review_date DATE               COMMENT '预计复查日',
    status            VARCHAR(20)  NOT NULL COMMENT 'PENDING / OBSERVING / REVIEW_PENDING / CLOSED',
    -- 该事件登记落定后马匹事件链所处版本：用来判断复查结论是否晚于最新伤病事件，
    -- 不依赖时间戳精度（同秒内连登两起事件也能正确判定「结论是否基于旧事件」）
    registered_version BIGINT     NOT NULL DEFAULT 0 COMMENT '事件登记时的事件链版本',
    -- 最近一次复查结论的冗余指针：马匹详情「最近一次复查结论」直接读这里
    latest_review_id  BIGINT               COMMENT '最近一次复查记录 id',
    created_by        VARCHAR(64)  NOT NULL COMMENT '登记人',
    created_role      VARCHAR(16)  NOT NULL COMMENT 'STAFF 普通工作人员 / MANAGER 负责人',
    closed_at         DATETIME             COMMENT '关闭时间（放行成功时写入）',
    closed_by         VARCHAR(64)           COMMENT '关闭人（放行负责人）',
    created_at        DATETIME     NOT NULL COMMENT '创建时间',
    updated_at        DATETIME     NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_health_event_no (event_no),
    KEY idx_event_horse_status (horse_id, status),
    KEY idx_event_review (latest_review_id),
    CONSTRAINT fk_event_horse FOREIGN KEY (horse_id) REFERENCES horse (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '马匹健康事件';

-- ---------------------------------------------------------------------------
-- 健康事件流转日志：只追加、永不修改、永不删除。
-- 每次登记 / 补充 / 流转 / 复查 / 放行落一条，处置历史不能被后续编辑覆盖。
-- request_id 是前端为一次点击生成的幂等键：重复点击拿到同一条记录，绝不产生两条。
-- ---------------------------------------------------------------------------
CREATE TABLE health_event_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    event_id    BIGINT       NOT NULL COMMENT '所属事件',
    horse_id    BIGINT       NOT NULL COMMENT '所属马匹（冗余，便于按马追溯）',
    action      VARCHAR(24)  NOT NULL COMMENT 'REGISTER 登记 / SUPPLEMENT 补充 / TRANSITION 流转 / REVIEW 复查 / RELEASE 复训放行',
    from_status VARCHAR(20)           COMMENT '流转前状态（首次登记为空）',
    to_status   VARCHAR(20)           COMMENT '流转后状态',
    note        VARCHAR(500) NOT NULL COMMENT '本次处置说明',
    operator    VARCHAR(64)  NOT NULL COMMENT '操作人',
    role        VARCHAR(16)  NOT NULL COMMENT 'STAFF / MANAGER',
    request_id  VARCHAR(64)           COMMENT '前端幂等键（同一操作重复提交共用一条日志）',
    created_at  DATETIME     NOT NULL COMMENT '操作时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_event_log_request (request_id),
    KEY idx_event_log_event (event_id, id),
    KEY idx_event_log_horse (horse_id, id),
    CONSTRAINT fk_event_log_event FOREIGN KEY (event_id) REFERENCES health_event (id),
    CONSTRAINT fk_event_log_horse FOREIGN KEY (horse_id) REFERENCES horse (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '健康事件处置历史（只追加）';

-- ---------------------------------------------------------------------------
-- 复查记录：一次复查一条；放行依据就是每个未关闭事件的「最后一条」复查
-- result：OBSERVE 继续观察 / RESCHEDULE 调整下次复查日 / PASS 复查合格申请放行
-- next_review_date：OBSERVE / RESCHEDULE 必填；PASS 不填表示长期有效，
-- 填了则是合格结论的有效期，过期后不能凭这条结论放行（复查已过期）。
-- ---------------------------------------------------------------------------
CREATE TABLE health_review (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    event_id         BIGINT       NOT NULL COMMENT '所属事件',
    horse_id         BIGINT       NOT NULL COMMENT '所属马匹',
    review_date      DATE         NOT NULL COMMENT '复查日期',
    result           VARCHAR(16)  NOT NULL COMMENT 'OBSERVE / RESCHEDULE / PASS',
    conclusion       VARCHAR(500) NOT NULL COMMENT '复查结论 / 说明',
    next_review_date DATE                  COMMENT '下次复查日（PASS 时可空=长期有效）',
    -- 复查依据版本：写入时马匹事件链版本。放行时最新事件的 registered_version
    -- 大于它，说明结论基于旧事件链（期间又登记了更新的伤病），必须拒绝。
    based_version     BIGINT      NOT NULL DEFAULT 0 COMMENT '复查所依据的事件链版本',
    reviewer         VARCHAR(64)  NOT NULL COMMENT '复查人',
    role             VARCHAR(16)  NOT NULL COMMENT 'STAFF / MANAGER',
    request_id       VARCHAR(64)           COMMENT '前端幂等键',
    created_at       DATETIME     NOT NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_review_request (request_id),
    KEY idx_review_event (event_id, id),
    CONSTRAINT fk_review_event FOREIGN KEY (event_id) REFERENCES health_event (id),
    CONSTRAINT fk_review_horse FOREIGN KEY (horse_id) REFERENCES horse (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '健康事件复查记录';

-- ---------------------------------------------------------------------------
-- 健康事件与受影响排期的关联：登记高风险事件时把该马未来未取消场次逐场挂进来，
-- 场次本身不删除，这里保留「是哪次事件、在什么时间影响了哪一场」的追溯链。
-- ---------------------------------------------------------------------------
CREATE TABLE health_event_session (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    event_id     BIGINT      NOT NULL COMMENT '来源健康事件',
    horse_id     BIGINT      NOT NULL COMMENT '马匹',
    session_id   BIGINT      NOT NULL COMMENT '受影响排期',
    marked_at    DATETIME    NOT NULL COMMENT '标记时间',
    marked_by    VARCHAR(64) NOT NULL COMMENT '标记操作人（事件登记人）',
    PRIMARY KEY (id),
    UNIQUE KEY uk_event_session (event_id, session_id),
    KEY idx_hes_horse (horse_id),
    CONSTRAINT fk_hes_event FOREIGN KEY (event_id) REFERENCES health_event (id),
    CONSTRAINT fk_hes_horse FOREIGN KEY (horse_id) REFERENCES horse (id),
    CONSTRAINT fk_hes_session FOREIGN KEY (session_id) REFERENCES lesson_session (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '健康事件影响的未来排期（可追溯，不删排期）';

-- ---------------------------------------------------------------------------
-- 值班班次：值班负责人建立，OPEN 进行中 -> CLOSED 已结束。
-- 交接期间允许新旧两个 OPEN 班次并存（下一班先建立、当班再结束），
-- 「班次链」的结构性操作（建班 / 关班 / 期间课程变更入队）都串行在全局序号锁上。
-- ---------------------------------------------------------------------------
CREATE TABLE duty_shift (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    shift_no        VARCHAR(32)  NOT NULL COMMENT '班次编号，唯一，形如 DS-000001',
    shift_date      DATE         NOT NULL COMMENT '值班日期',
    leader_name     VARCHAR(64)  NOT NULL COMMENT '值班负责人姓名',
    handover_note   VARCHAR(1000)         COMMENT '当班交接总说明（可多次补充）',
    status          VARCHAR(16)  NOT NULL COMMENT 'OPEN 进行中 / CLOSED 已结束',
    -- 班次版本：事项分派 / 新事项挂入 / 关班都 +1，旧页面拿旧版本提交直接 409
    item_version    BIGINT       NOT NULL DEFAULT 0 COMMENT '交接清单版本（乐观锁）',
    -- 开班幂等键：重复点击 / 网络重试只取回同一个班次，绝不连开两个班
    create_request_id VARCHAR(64)          COMMENT '开班请求幂等键',
    started_at      DATETIME     NOT NULL COMMENT '开班时间',
    closed_at       DATETIME              COMMENT '结班时间',
    created_at      DATETIME     NOT NULL COMMENT '创建时间',
    updated_at      DATETIME     NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_duty_shift_no (shift_no),
    UNIQUE KEY uk_duty_shift_request (create_request_id),
    KEY idx_duty_shift_date (shift_date),
    KEY idx_duty_shift_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '值班班次';

-- ---------------------------------------------------------------------------
-- 全局序号锁：整库固定一行（id=1）。
-- 开班 / 关班 / 交接期间课程变更入队先抢这把锁，
-- 保证「新出现的课程变更进入正确班次」以及「未承接事项不会在关班时漏派」，
-- 不依赖多条 shift 行的加锁顺序，从根上避免循环等待。
-- ---------------------------------------------------------------------------
CREATE TABLE duty_seq_lock (
    id          BIGINT      NOT NULL,
    holder      VARCHAR(64)          COMMENT '当前持锁说明（仅排障用）',
    updated_at  DATETIME             COMMENT '最近加锁时间',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '值班交接链全局序号锁';

INSERT INTO duty_seq_lock (id, holder, updated_at) VALUES (1, NULL, NOW());

-- ---------------------------------------------------------------------------
-- 交接事项（未结事项）：开班时从当班课程 / 马匹状态 / 栏位安排生成快照，
-- 值班期间的课程变更实时挂入当前最新 OPEN 班次；普通教练可补充、负责人可改优先级 / 分派。
-- 事项状态机：PENDING 待承接 -> ACCEPTED 已承接（终态）。
-- 承接只由「目标班次负责人」逐项确认；已承接事项任何旧班次都不能改回未处理。
-- 未承接事项在班次结束后靠 target_shift_id 继续挂在下一班首页，不因班次结束消失。
-- 全局唯一键保证同一业务对象（同一排期变更 / 同一匹马 / 同一栏位 / 同一手工键）
-- 在未承接时只存在一条，快照生成与实时挂入共用这套去重。
-- ---------------------------------------------------------------------------
CREATE TABLE handover_item (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    item_no           VARCHAR(32)  NOT NULL COMMENT '事项编号，唯一，形如 HI-000001',
    shift_id          BIGINT       NOT NULL COMMENT '来源班次（事项由哪个班次产生）',
    target_shift_id   BIGINT                COMMENT '承接班次（分派给哪个班次；null=尚未分派）',
    item_type         VARCHAR(24)  NOT NULL COMMENT 'SESSION 课程 / HORSE 马匹 / STALL 栏位 / MANUAL 手工',
    ref_type          VARCHAR(24)           COMMENT 'SESSION_CHANGE 课程变更 / HORSE_STATUS 马匹状态 / STALL_MAINT 栏位维护 / MANUAL',
    ref_id            BIGINT                COMMENT '关联对象 id（排期 / 马匹 / 栏位）',
    change_kind       VARCHAR(24)           COMMENT '课程变更细分：NEW 新排期 / CANCELED 取消 / AFFECTED 健康受影响 / FULL 已满员',
    title             VARCHAR(200) NOT NULL COMMENT '事项标题',
    detail            VARCHAR(1000)         COMMENT '事项快照说明（开班时的状态定格）',
    priority          VARCHAR(16)  NOT NULL COMMENT 'HIGH 高 / NORMAL 普通 / LOW 低',
    owner_name        VARCHAR(64)           COMMENT '负责教练（普通教练只能补充分派给自己的事项）',
    status            VARCHAR(16)  NOT NULL COMMENT 'PENDING 待承接 / ACCEPTED 已承接（终态）',
    expected_version  BIGINT       NOT NULL DEFAULT 0 COMMENT '事项自身版本（补充 / 改优先级 / 分派 / 承接的乐观锁）',
    accepted_at       DATETIME              COMMENT '承接时间',
    accepted_by       VARCHAR(64)           COMMENT '承接人（目标班次负责人）',
    accepted_note     VARCHAR(500)          COMMENT '承接说明',
    -- 关班时仍未承接：负责人逐项记录延期原因后才允许完成交接（关班原子写入）
    delay_reason      VARCHAR(500)          COMMENT '延期原因（未承接即结班时逐项填写）',
    delayed_at        DATETIME              COMMENT '延期记录时间',
    delayed_by        VARCHAR(64)           COMMENT '记录延期的负责人',
    request_id        VARCHAR(64)           COMMENT '事项产生时的幂等键',
    -- 条件唯一去重：只有未承接（PENDING）事项参与唯一约束；已承接事项留 NULL（MySQL 唯一索引允许多个 NULL），
    -- 这样同一业务对象「承接后再次变更」可以生成新的未承接事项
    dedup_shift_id    BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'PENDING' THEN shift_id ELSE NULL END) VIRTUAL
                          COMMENT '去重用生成列：未承接=来源班次，已承接=NULL',
    created_at        DATETIME     NOT NULL COMMENT '创建时间',
    updated_at        DATETIME     NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_handover_item_no (item_no),
    -- 未承接事项业务唯一键：同一班次对同一业务对象只保留一条未承接事项（已承接不参与）
    UNIQUE KEY uk_item_open_dedup (dedup_shift_id, ref_type, ref_id, change_kind),
    KEY idx_item_shift (shift_id),
    KEY idx_item_target_status (target_shift_id, status),
    KEY idx_item_status (status),
    CONSTRAINT fk_item_shift FOREIGN KEY (shift_id) REFERENCES duty_shift (id),
    CONSTRAINT fk_item_target_shift FOREIGN KEY (target_shift_id) REFERENCES duty_shift (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '值班交接事项';

-- ---------------------------------------------------------------------------
-- 承接确认记录：逐项承接，一个事项至多一条（uk 唯一约束兜底）。
-- 重复点击 / 网络重试携带同一 requestId 只会取回同一条，绝不产生重复承接记录。
-- ---------------------------------------------------------------------------
CREATE TABLE handover_acceptance (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    item_id     BIGINT       NOT NULL COMMENT '承接的事项',
    shift_id    BIGINT       NOT NULL COMMENT '承接班次（下一班）',
    acceptor    VARCHAR(64)  NOT NULL COMMENT '承接负责人',
    note        VARCHAR(500)          COMMENT '承接说明',
    request_id  VARCHAR(64)           COMMENT '前端幂等键',
    created_at  DATETIME     NOT NULL COMMENT '承接时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_acceptance_item (item_id),
    UNIQUE KEY uk_acceptance_request (request_id),
    KEY idx_acceptance_shift (shift_id),
    CONSTRAINT fk_acceptance_item FOREIGN KEY (item_id) REFERENCES handover_item (id),
    CONSTRAINT fk_acceptance_shift FOREIGN KEY (shift_id) REFERENCES duty_shift (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '交接事项承接确认';

-- ---------------------------------------------------------------------------
-- 交接事项操作流水：只追加，永不修改、永不删除。
-- CREATE 建项 / SUPPLEMENT 补充 / PRIORITY 优先级 / ASSIGN 分派负责人 /
-- DISPATCH 分派班次 / ACCEPT 承接 / DELAY 延期。
-- 「普通教练只能补充自己负责事项」「已承接不能改回」等审计都靠这条链回放。
-- ---------------------------------------------------------------------------
CREATE TABLE handover_item_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    item_id     BIGINT       NOT NULL COMMENT '所属事项',
    action      VARCHAR(24)  NOT NULL COMMENT 'CREATE / SUPPLEMENT / PRIORITY / ASSIGN / DISPATCH / ACCEPT / DELAY',
    note        VARCHAR(500) NOT NULL COMMENT '本次操作说明',
    operator    VARCHAR(64)  NOT NULL COMMENT '操作人',
    role        VARCHAR(16)  NOT NULL COMMENT 'STAFF 普通教练 / MANAGER 值班负责人',
    request_id  VARCHAR(64)           COMMENT '前端幂等键（重复提交共用一条流水）',
    created_at  DATETIME     NOT NULL COMMENT '操作时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_item_log_request (request_id),
    KEY idx_item_log_item (item_id, id),
    CONSTRAINT fk_item_log_item FOREIGN KEY (item_id) REFERENCES handover_item (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT '交接事项操作流水（只追加）';

-- ===========================================================================
-- 种子数据
-- ===========================================================================

-- 马匹：H-005 处于休养、H-006 已退役（终态，用于触发状态机与「退役禁止排课」）
INSERT INTO horse (id, horse_no, name, breed, gender, birth_year, status, ride_level, created_at, updated_at) VALUES
(1, 'H-001', '追风',   '温血马',     'MALE',    2016, 'ACTIVE',  'INTERMEDIATE',   NOW(), NOW()),
(2, 'H-002', '踏雪',   '荷兰温血',   'MALE',    2018, 'ACTIVE',  'BEGINNER_SAFE',  NOW(), NOW()),
(3, 'H-003', '灰姑娘', '阿拉伯马',   'FEMALE',  2017, 'ACTIVE',  'BEGINNER_SAFE',  NOW(), NOW()),
(4, 'H-004', '黑旋风', '汉诺威',     'GELDING', 2015, 'ACTIVE',  'ADVANCED',       NOW(), NOW()),
(5, 'H-005', '玉兔',   '蒙古马',     'FEMALE',  2019, 'RESTING', 'BEGINNER_SAFE',  NOW(), NOW()),
(6, 'H-006', '老伙计', '温血马',     'GELDING', 2011, 'RETIRED', 'BEGINNER_SAFE',  NOW(), NOW());

-- 马匹健康档案（副表），与主表一一对应
INSERT INTO horse_health (horse_id, last_check_date, vaccine_count, weight_kg, health_note) VALUES
(1, DATE_SUB(CURDATE(), INTERVAL 20 DAY), 6, 545.0, '状态良好，蹄铁上月已更换'),
(2, DATE_SUB(CURDATE(), INTERVAL 12 DAY), 5, 512.5, '状态良好，轻微皮肤敏感'),
(3, DATE_SUB(CURDATE(), INTERVAL 40 DAY), 4, 468.0, '偏瘦，需增加精饲料'),
(4, DATE_SUB(CURDATE(), INTERVAL 8 DAY),  7, 590.0, '状态优秀，可承担高阶障碍课'),
(5, DATE_SUB(CURDATE(), INTERVAL 3 DAY),  3, 402.0, '轻度跛行，休养至下次复查'),
(6, DATE_SUB(CURDATE(), INTERVAL 120 DAY), 9, 528.0, '老龄退役，仅做日常护理');

-- 栏位：STM-001/002/005/007 已占用，STM-003/006/009 空闲，STM-008 维护中
INSERT INTO stall (id, stall_no, barn_name, area_sqm, status, horse_id, created_at, updated_at) VALUES
(1, 'STM-001', 'A 区马房', 16.0, 'OCCUPIED',    1,    NOW(), NOW()),
(2, 'STM-002', 'A 区马房', 16.0, 'OCCUPIED',    2,    NOW(), NOW()),
(3, 'STM-003', 'A 区马房', 16.0, 'IDLE',        NULL, NOW(), NOW()),
(4, 'STM-004', 'A 区马房', 18.5, 'IDLE',        NULL, NOW(), NOW()),
(5, 'STM-005', 'B 区马房', 15.0, 'OCCUPIED',    3,    NOW(), NOW()),
(6, 'STM-006', 'B 区马房', 15.0, 'IDLE',        NULL, NOW(), NOW()),
(7, 'STM-007', 'B 区马房', 20.0, 'OCCUPIED',    4,    NOW(), NOW()),
(8, 'STM-008', 'B 区马房', 15.0, 'MAINTENANCE', NULL, NOW(), NOW()),
(9, 'STM-009', 'C 区放牧场', 60.0, 'IDLE',      NULL, NOW(), NOW());

-- 教练：C-04 休假中（用于触发「休假教练不能排期」）
INSERT INTO coach (id, coach_no, name, level, phone, status, created_at, updated_at) VALUES
(1, 'C-01', '陈骁', 'CHIEF',  '13900000101', 'ON_DUTY', NOW(), NOW()),
(2, 'C-02', '林婉', 'JUNIOR', '13900000102', 'ON_DUTY', NOW(), NOW()),
(3, 'C-03', '赵崇', 'SENIOR', '13900000103', 'ON_DUTY', NOW(), NOW()),
(4, 'C-04', '何静', 'JUNIOR', '13900000104', 'LEAVE',   NOW(), NOW());

-- 课程：L-ADV-01 / L-ADV-02 / L-PRV-01 属于进阶课与私教课，未通过初级考核不能约
INSERT INTO lesson (id, lesson_no, name, category, capacity, duration_min, price, times_cost, status, created_at, updated_at) VALUES
(1, 'L-BAS-01', '初级骑乘入门', 'BASIC',    6, 60,  380.00, 1, 'ON', NOW(), NOW()),
(2, 'L-BAS-02', '初级场地训练', 'BASIC',    4, 60,  420.00, 1, 'ON', NOW(), NOW()),
(3, 'L-ADV-01', '进阶障碍跨越', 'ADVANCED', 4, 60,  680.00, 2, 'ON', NOW(), NOW()),
(4, 'L-ADV-02', '进阶盛装舞步', 'ADVANCED', 3, 60,  720.00, 2, 'ON', NOW(), NOW()),
(5, 'L-PRV-01', '一对一私教课', 'PRIVATE',  1, 60,  980.00, 2, 'ON', NOW(), NOW());

-- 课程排期：日期用 CURDATE() 派生，保证「训练日历」里永远有未来 6 天的内容
-- 场次 2 与 8 已满员（触发课程容量）；场次 12 已取消（触发「必须关联已排期的课程」）
INSERT INTO lesson_session (id, lesson_id, coach_id, horse_id, session_date, start_time, end_time, capacity, booked_count, status, created_at, updated_at) VALUES
(1,  1, 1, 2,    DATE_ADD(CURDATE(), INTERVAL 1 DAY), '09:00', '10:00', 6, 2, 'SCHEDULED', NOW(), NOW()),
(2,  2, 2, 3,    DATE_ADD(CURDATE(), INTERVAL 1 DAY), '10:00', '11:00', 4, 4, 'FULL',      NOW(), NOW()),
(3,  3, 3, 1,    DATE_ADD(CURDATE(), INTERVAL 1 DAY), '14:00', '15:00', 4, 1, 'SCHEDULED', NOW(), NOW()),
(4,  1, 1, 2,    DATE_ADD(CURDATE(), INTERVAL 2 DAY), '09:00', '10:00', 6, 1, 'SCHEDULED', NOW(), NOW()),
(5,  3, 3, 4,    DATE_ADD(CURDATE(), INTERVAL 2 DAY), '16:00', '17:00', 4, 0, 'SCHEDULED', NOW(), NOW()),
(6,  4, 1, 4,    DATE_ADD(CURDATE(), INTERVAL 3 DAY), '11:00', '12:00', 3, 0, 'SCHEDULED', NOW(), NOW()),
(7,  1, 2, 2,    DATE_ADD(CURDATE(), INTERVAL 3 DAY), '15:00', '16:00', 6, 3, 'SCHEDULED', NOW(), NOW()),
(8,  5, 3, 1,    DATE_ADD(CURDATE(), INTERVAL 4 DAY), '10:00', '11:00', 1, 1, 'FULL',      NOW(), NOW()),
(9,  2, 1, 3,    DATE_ADD(CURDATE(), INTERVAL 5 DAY), '15:00', '16:00', 4, 0, 'SCHEDULED', NOW(), NOW()),
(10, 3, 2, 4,    DATE_SUB(CURDATE(), INTERVAL 3 DAY), '14:00', '15:00', 4, 3, 'SCHEDULED', NOW(), NOW()),
(11, 1, 1, 2,    DATE_SUB(CURDATE(), INTERVAL 5 DAY), '09:00', '10:00', 6, 2, 'SCHEDULED', NOW(), NOW()),
(12, 5, 3, 1,    DATE_ADD(CURDATE(), INTERVAL 2 DAY), '15:00', '16:00', 1, 0, 'CANCELED',  NOW(), NOW());

-- 会员：M-005 未通过初级考核；M-006 剩余次数为 0；M-004 次数够但余额不足
INSERT INTO club_member (id, member_no, name, phone, level, card_balance, card_times, passed_basic, status, created_at, updated_at) VALUES
(1, 'M-001', '周雨桐', '13800000201', 'GOLD',    3200.00, 12, 1, 'NORMAL', NOW(), NOW()),
(2, 'M-002', '吴启航', '13800000202', 'SILVER',   800.00,  4, 1, 'NORMAL', NOW(), NOW()),
(3, 'M-003', '郑晓岚', '13800000203', 'DIAMOND', 12000.00, 30, 1, 'NORMAL', NOW(), NOW()),
(4, 'M-004', '孙浩',   '13800000204', 'NORMAL',   200.00,  2, 1, 'NORMAL', NOW(), NOW()),
(5, 'M-005', '李沐',   '13800000205', 'NORMAL',  1500.00,  6, 0, 'NORMAL', NOW(), NOW()),
(6, 'M-006', '何嘉宁', '13800000206', 'SILVER',  1500.00,  0, 1, 'NORMAL', NOW(), NOW());

-- 骑乘记录：booked_count 与「未取消的记录条数」保持一致
INSERT INTO riding_record (id, member_id, session_id, horse_id, record_date, start_time, end_time, status, fee, times_used, remark, created_at, updated_at) VALUES
(1,  1, 1,  2, DATE_ADD(CURDATE(), INTERVAL 1 DAY), '09:00', '10:00', 'BOOKED',    342.00, 1, '首次上马',       NOW(), NOW()),
(2,  3, 1,  2, DATE_ADD(CURDATE(), INTERVAL 1 DAY), '09:00', '10:00', 'BOOKED',    304.00, 1, NULL,             NOW(), NOW()),
(3,  1, 2,  3, DATE_ADD(CURDATE(), INTERVAL 1 DAY), '10:00', '11:00', 'BOOKED',    378.00, 1, NULL,             NOW(), NOW()),
(4,  2, 2,  3, DATE_ADD(CURDATE(), INTERVAL 1 DAY), '10:00', '11:00', 'BOOKED',    399.00, 1, NULL,             NOW(), NOW()),
(5,  3, 2,  3, DATE_ADD(CURDATE(), INTERVAL 1 DAY), '10:00', '11:00', 'BOOKED',    336.00, 1, NULL,             NOW(), NOW()),
(6,  4, 2,  3, DATE_ADD(CURDATE(), INTERVAL 1 DAY), '10:00', '11:00', 'BOOKED',    420.00, 1, NULL,             NOW(), NOW()),
(7,  2, 3,  1, DATE_ADD(CURDATE(), INTERVAL 1 DAY), '14:00', '15:00', 'BOOKED',    646.00, 2, '备赛训练',       NOW(), NOW()),
(8,  2, 4,  2, DATE_ADD(CURDATE(), INTERVAL 2 DAY), '09:00', '10:00', 'BOOKED',    361.00, 1, NULL,             NOW(), NOW()),
(9,  1, 7,  2, DATE_ADD(CURDATE(), INTERVAL 3 DAY), '15:00', '16:00', 'COMPLETED', 342.00, 1, NULL,             NOW(), NOW()),
(10, 2, 7,  2, DATE_ADD(CURDATE(), INTERVAL 3 DAY), '15:00', '16:00', 'COMPLETED', 361.00, 1, NULL,             NOW(), NOW()),
(11, 3, 7,  2, DATE_ADD(CURDATE(), INTERVAL 3 DAY), '15:00', '16:00', 'COMPLETED', 304.00, 1, NULL,             NOW(), NOW()),
(12, 1, 10, 4, DATE_SUB(CURDATE(), INTERVAL 3 DAY), '14:00', '15:00', 'COMPLETED', 612.00, 2, NULL,             NOW(), NOW()),
(13, 2, 10, 4, DATE_SUB(CURDATE(), INTERVAL 3 DAY), '14:00', '15:00', 'COMPLETED', 646.00, 2, NULL,             NOW(), NOW()),
(14, 3, 10, 4, DATE_SUB(CURDATE(), INTERVAL 3 DAY), '14:00', '15:00', 'COMPLETED', 544.00, 2, NULL,             NOW(), NOW()),
(15, 4, 10, 4, DATE_SUB(CURDATE(), INTERVAL 3 DAY), '14:00', '15:00', 'CANCELED',  680.00, 2, '临时有事取消',   NOW(), NOW()),
(16, 1, 8,  1, DATE_ADD(CURDATE(), INTERVAL 4 DAY), '10:00', '11:00', 'BOOKED',    784.00, 2, '私教',           NOW(), NOW()),
(17, 2, 11, 2, DATE_SUB(CURDATE(), INTERVAL 5 DAY), '09:00', '10:00', 'COMPLETED', 361.00, 1, NULL,             NOW(), NOW()),
(18, 3, 11, 2, DATE_SUB(CURDATE(), INTERVAL 5 DAY), '09:00', '10:00', 'COMPLETED', 304.00, 1, NULL,             NOW(), NOW());

-- ===========================================================================
-- 健康事件种子数据
-- 设计口径：让「待复查且已逾期 + 最近复查结论为继续观察」一进来就能在处置台看到，
-- 并且玉兔（H-005）休养中、其未来排期被标记受影响，可直接追溯到 HE-000001。
-- ===========================================================================

-- H-005 玉兔：右前肢轻度跛行，事件当前停在「待复查」，预计复查日已过 1 天（触发逾期筛选）
INSERT INTO health_event
(id, event_no, horse_id, occurred_at, severity, symptom, treatment_advice, expected_review_date,
 status, registered_version, latest_review_id, created_by, created_role, closed_at, closed_by, created_at, updated_at) VALUES
(1, 'HE-000001', 5, DATE_SUB(NOW(), INTERVAL 5 DAY), 'MEDIUM',
 '右前肢轻度跛行，快步时点头明显，蹄温略高',
 '停止跳跃与快步训练，冰敷右前肢每日两次，外敷消炎膏，3-5 天后复查跛行分级',
 DATE_SUB(CURDATE(), INTERVAL 1 DAY),
 'REVIEW_PENDING', 1, 1, '值班员小周', 'STAFF', NULL, NULL,
 DATE_SUB(NOW(), INTERVAL 5 DAY), DATE_SUB(NOW(), INTERVAL 2 DAY));

-- 处置历史（只追加）：登记 -> 转观察中 -> 第一次复查继续观察（同时把下次复查日调到昨天）
INSERT INTO health_event_log
(id, event_id, horse_id, action, from_status, to_status, note, operator, role, request_id, created_at) VALUES
(1, 1, 5, 'REGISTER',   NULL,           'PENDING',        '晨间遛马发现跛行，立即停训并报值班兽医',                 '值班员小周', 'STAFF', 'seed-he-1-register',   DATE_SUB(NOW(), INTERVAL 5 DAY)),
(2, 1, 5, 'TRANSITION', 'PENDING',      'OBSERVING',      '已冰敷处理，生命体征平稳，转入观察',                     '值班员小周', 'STAFF', 'seed-he-1-observe',    DATE_SUB(NOW(), INTERVAL 5 DAY)),
(3, 1, 5, 'REVIEW',     'OBSERVING',    'REVIEW_PENDING', '跛行由 2/5 级降至 1/5 级，继续观察，下次复查日定在次日', '兽医老李',   'STAFF', 'seed-he-1-review-1',   DATE_SUB(NOW(), INTERVAL 2 DAY));

-- 最近一次复查结论：继续观察（马匹详情页直接展示这条）
INSERT INTO health_review
(id, event_id, horse_id, review_date, result, conclusion, next_review_date, based_version, reviewer, role, request_id, created_at) VALUES
(1, 1, 5, DATE_SUB(CURDATE(), INTERVAL 2 DAY), 'OBSERVE',
 '跛行减轻但未完全消除，仍不可复训，继续观察并按原方案冰敷',
 DATE_SUB(CURDATE(), INTERVAL 1 DAY),
 3, '兽医老李', 'STAFF', 'seed-he-1-review', DATE_SUB(NOW(), INTERVAL 2 DAY));

-- H-005 事件登记时它名下的未来场次：不删除、标为受影响，并挂到事件上可追溯
UPDATE lesson_session
SET health_affected = 1
WHERE horse_id = 5
  AND status <> 'CANCELED'
  AND session_date >= CURDATE();

INSERT INTO health_event_session (event_id, horse_id, session_id, marked_at, marked_by)
SELECT 1, 5, id, DATE_SUB(NOW(), INTERVAL 5 DAY), '值班员小周'
FROM lesson_session
WHERE horse_id = 5
  AND status <> 'CANCELED'
  AND session_date >= CURDATE();

-- 事件链上一共动过 3 次（登记 / 流转 / 复查），马匹健康版本与之一致
UPDATE horse SET health_version = 3 WHERE id = 5;

-- ===========================================================================
-- 值班交接种子数据
-- 设计口径：一进来首页（值班交接台）就能看到「下一班仍有 1 条未承接事项」，
-- 并且历史班次里同时存在「已逐项承接」与「记录延期原因后结班」两种情形。
--   DS-000001：昨天的班，负责人陈骁，今早已结班
--     HI-1 已满员场次（明天 10:00）—— 已分派给 DS-2 且赵崇已逐项承接
--     HI-2 已满员私教（4 天后）    —— DS-1 结班时仍未承接，逐项记录延期原因后结班，
--                                     事项保持待承接继续挂在 DS-2 首页（跨班次不消失）
--     HI-3 玉兔休养待复查          —— 已分派给 DS-2 且已承接
--   DS-000002：今天的班，负责人赵崇，进行中
--     开班快照重新定格：两场满员课、玉兔休养、STM-008 维护中
-- ===========================================================================
INSERT INTO duty_shift
(id, shift_no, shift_date, leader_name, handover_note, status, item_version, started_at, closed_at, created_at, updated_at) VALUES
(1, 'DS-000001', DATE_SUB(CURDATE(), INTERVAL 1 DAY), '陈骁',
 '昨夜一切正常；玉兔跛行复查逾期已在跟进，两场满员课请下一班留意到场情况。',
 'CLOSED', 5,
 DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 8 HOUR),
 DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 8 HOUR)),
(2, 'DS-000002', CURDATE(), '赵崇',
 NULL, 'OPEN', 4,
 DATE_SUB(NOW(), INTERVAL 8 HOUR), NULL,
 DATE_SUB(NOW(), INTERVAL 8 HOUR), DATE_SUB(NOW(), INTERVAL 8 HOUR));

-- DS-1 产生的事项（开班快照）
INSERT INTO handover_item
(id, item_no, shift_id, target_shift_id, item_type, ref_type, ref_id, change_kind,
 title, detail, priority, owner_name, status, expected_version,
 accepted_at, accepted_by, accepted_note, delay_reason, delayed_at, delayed_by, request_id, created_at, updated_at) VALUES
(1, 'HI-000001', 1, 2, 'SESSION', 'SESSION_CHANGE', 2, 'FULL',
 '已满员课程：明天 10:00 初级场地训练（林婉）',
 '场次 #2 已约满 4/4，明天 10:00-11:00，教练林婉、用马灰姑娘；请留意到场与候补。',
 'NORMAL', '林婉', 'ACCEPTED', 2,
 DATE_SUB(NOW(), INTERVAL 7 HOUR), '赵崇', '已核对名单，4 人均已确认到场。',
 NULL, NULL, NULL, 'seed-hi-1',
 DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 7 HOUR)),
(2, 'HI-000002', 1, 2, 'SESSION', 'SESSION_CHANGE', 8, 'FULL',
 '已满员私教：4 天后 10:00 一对一私教课（赵崇）',
 '场次 #8 私教已约满 1/1，会员吴启航；备赛期间用马追风，需确认是否加开候补场。',
 'NORMAL', '赵崇', 'PENDING', 1,
 NULL, NULL, NULL,
 '会员临时改需求未联系上，吴启航今天到店，下一班当面确认是否改约或加场。',
 DATE_SUB(NOW(), INTERVAL 8 HOUR), '陈骁', 'seed-hi-2',
 DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 8 HOUR)),
(3, 'HI-000003', 1, 2, 'HORSE', 'HORSE_STATUS', 5, NULL,
 '马匹休养：玉兔（H-005）轻度跛行待复查',
 '右前肢轻度跛行，健康事件 HE-000001 停在待复查且预计复查日已过，名下未来排期已标记受影响，复训须负责人放行。',
 'HIGH', NULL, 'ACCEPTED', 2,
 DATE_SUB(NOW(), INTERVAL 7 HOUR), '赵崇', '已知悉，今天联系兽医老李安排复查。',
 NULL, NULL, NULL, 'seed-hi-3',
 DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_SUB(NOW(), INTERVAL 7 HOUR)),
-- DS-2 开班快照（未分派，本版处理）
(4, 'HI-000004', 2, NULL, 'SESSION', 'SESSION_CHANGE', 2, 'FULL',
 '已满员课程：明天 10:00 初级场地训练（林婉）',
 '场次 #2 已约满 4/4，明天 10:00-11:00，教练林婉、用马灰姑娘。',
 'NORMAL', '林婉', 'PENDING', 0,
 NULL, NULL, NULL, NULL, NULL, NULL, 'seed-hi-4',
 DATE_SUB(NOW(), INTERVAL 8 HOUR), DATE_SUB(NOW(), INTERVAL 8 HOUR)),
(5, 'HI-000005', 2, NULL, 'SESSION', 'SESSION_CHANGE', 8, 'FULL',
 '已满员私教：4 天后 10:00 一对一私教课（赵崇）',
 '场次 #8 私教已约满 1/1，会员吴启航。',
 'NORMAL', '赵崇', 'PENDING', 0,
 NULL, NULL, NULL, NULL, NULL, NULL, 'seed-hi-5',
 DATE_SUB(NOW(), INTERVAL 8 HOUR), DATE_SUB(NOW(), INTERVAL 8 HOUR)),
(6, 'HI-000006', 2, NULL, 'HORSE', 'HORSE_STATUS', 5, NULL,
 '马匹休养：玉兔（H-005）轻度跛行待复查',
 '健康事件 HE-000001 待复查且预计复查日已过 1 天；休养中禁止排课，复训须负责人放行。',
 'HIGH', NULL, 'PENDING', 0,
 NULL, NULL, NULL, NULL, NULL, NULL, 'seed-hi-6',
 DATE_SUB(NOW(), INTERVAL 8 HOUR), DATE_SUB(NOW(), INTERVAL 8 HOUR)),
(7, 'HI-000007', 2, NULL, 'STALL', 'STALL_MAINT', 8, NULL,
 '栏位维护中：STM-008（B 区马房）',
 'STM-008 维护中，禁止入栏；完工后需做出栏验收再恢复空闲。',
 'NORMAL', NULL, 'PENDING', 0,
 NULL, NULL, NULL, NULL, NULL, NULL, 'seed-hi-7',
 DATE_SUB(NOW(), INTERVAL 8 HOUR), DATE_SUB(NOW(), INTERVAL 8 HOUR));

-- 承接记录：每个事项至多一条
INSERT INTO handover_acceptance (id, item_id, shift_id, acceptor, note, request_id, created_at) VALUES
(1, 1, 2, '赵崇', '已核对名单，4 人均已确认到场。', 'seed-accept-1', DATE_SUB(NOW(), INTERVAL 7 HOUR)),
(2, 3, 2, '赵崇', '已知悉，今天联系兽医老李安排复查。', 'seed-accept-3', DATE_SUB(NOW(), INTERVAL 7 HOUR));

-- 操作流水（只追加）
INSERT INTO handover_item_log (id, item_id, action, note, operator, role, request_id, created_at) VALUES
(1, 1, 'CREATE', '开班快照：场次 #2 已满员 4/4，列入交接清单', '陈骁', 'MANAGER', 'seed-hi-1', DATE_SUB(NOW(), INTERVAL 1 DAY)),
(2, 1, 'DISPATCH', '随 DS-000001 交接分派给下一班 DS-000002', '陈骁', 'MANAGER', 'seed-hi-1-dispatch', DATE_SUB(NOW(), INTERVAL 8 HOUR)),
(3, 1, 'ACCEPT', '已核对名单，4 人均已确认到场。', '赵崇', 'MANAGER', 'seed-accept-1', DATE_SUB(NOW(), INTERVAL 7 HOUR)),
(4, 2, 'CREATE', '开班快照：场次 #8 私教已满员，列入交接清单', '陈骁', 'MANAGER', 'seed-hi-2', DATE_SUB(NOW(), INTERVAL 1 DAY)),
(5, 2, 'DELAY', '会员临时改需求未联系上，吴启航今天到店，下一班当面确认是否改约或加场。', '陈骁', 'MANAGER', 'seed-hi-2-delay', DATE_SUB(NOW(), INTERVAL 8 HOUR)),
(6, 2, 'DISPATCH', '结班时仍未承接，已记录延期原因并随交接转给 DS-000002', '陈骁', 'MANAGER', 'seed-hi-2-dispatch', DATE_SUB(NOW(), INTERVAL 8 HOUR)),
(7, 3, 'CREATE', '开班快照：玉兔休养中、复查逾期，列入交接清单', '陈骁', 'MANAGER', 'seed-hi-3', DATE_SUB(NOW(), INTERVAL 1 DAY)),
(8, 3, 'DISPATCH', '随 DS-000001 交接分派给下一班 DS-000002', '陈骁', 'MANAGER', 'seed-hi-3-dispatch', DATE_SUB(NOW(), INTERVAL 8 HOUR)),
(9, 3, 'ACCEPT', '已知悉，今天联系兽医老李安排复查。', '赵崇', 'MANAGER', 'seed-accept-3', DATE_SUB(NOW(), INTERVAL 7 HOUR)),
(10, 4, 'CREATE', '开班快照：场次 #2 已满员 4/4，列入交接清单', '赵崇', 'MANAGER', 'seed-hi-4', DATE_SUB(NOW(), INTERVAL 8 HOUR)),
(11, 5, 'CREATE', '开班快照：场次 #8 私教已满员，列入交接清单', '赵崇', 'MANAGER', 'seed-hi-5', DATE_SUB(NOW(), INTERVAL 8 HOUR)),
(12, 6, 'CREATE', '开班快照：玉兔休养中、复查逾期，列入交接清单', '赵崇', 'MANAGER', 'seed-hi-6', DATE_SUB(NOW(), INTERVAL 8 HOUR)),
(13, 7, 'CREATE', '开班快照：STM-008 维护中，列入交接清单', '赵崇', 'MANAGER', 'seed-hi-7', DATE_SUB(NOW(), INTERVAL 8 HOUR));
