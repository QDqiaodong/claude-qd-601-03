-- 测试用最小建表（H2 MySQL 兼容模式）：只覆盖值班交接链路及其快照依赖的表。
DROP TABLE IF EXISTS handover_item_log;
DROP TABLE IF EXISTS handover_acceptance;
DROP TABLE IF EXISTS handover_item;
DROP TABLE IF EXISTS duty_seq_lock;
DROP TABLE IF EXISTS duty_shift;
DROP TABLE IF EXISTS health_event;
DROP TABLE IF EXISTS riding_record;
DROP TABLE IF EXISTS lesson_session;
DROP TABLE IF EXISTS lesson;
DROP TABLE IF EXISTS coach;
DROP TABLE IF EXISTS stall;
DROP TABLE IF EXISTS horse_health;
DROP TABLE IF EXISTS horse;
DROP TABLE IF EXISTS club_member;

CREATE TABLE horse (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    horse_no VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(64) NOT NULL,
    breed VARCHAR(32),
    gender VARCHAR(16),
    birth_year INT,
    status VARCHAR(16) NOT NULL,
    ride_level VARCHAR(16) NOT NULL,
    health_version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME,
    updated_at DATETIME
);

CREATE TABLE horse_health (
    horse_id BIGINT NOT NULL PRIMARY KEY,
    last_check_date DATE,
    vaccine_count INT,
    weight_kg DECIMAL(6,1),
    health_note VARCHAR(255)
);

CREATE TABLE stall (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    stall_no VARCHAR(32) NOT NULL UNIQUE,
    barn_name VARCHAR(64) NOT NULL,
    area_sqm DECIMAL(6,1),
    status VARCHAR(16) NOT NULL,
    horse_id BIGINT UNIQUE,
    created_at DATETIME,
    updated_at DATETIME
);

CREATE TABLE coach (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    coach_no VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(64) NOT NULL,
    level VARCHAR(16) NOT NULL,
    phone VARCHAR(32),
    status VARCHAR(16) NOT NULL,
    created_at DATETIME,
    updated_at DATETIME
);

CREATE TABLE lesson (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    lesson_no VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(64) NOT NULL,
    category VARCHAR(16) NOT NULL,
    capacity INT NOT NULL,
    duration_min INT NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    times_cost INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME,
    updated_at DATETIME
);

CREATE TABLE lesson_session (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    lesson_id BIGINT NOT NULL,
    coach_id BIGINT NOT NULL,
    horse_id BIGINT,
    session_date DATE NOT NULL,
    start_time VARCHAR(5) NOT NULL,
    end_time VARCHAR(5) NOT NULL,
    capacity INT NOT NULL,
    booked_count INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    health_affected TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME,
    updated_at DATETIME
);

CREATE TABLE club_member (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    member_no VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(64) NOT NULL,
    phone VARCHAR(32),
    level VARCHAR(16) NOT NULL,
    card_balance DECIMAL(10,2) NOT NULL,
    card_times INT NOT NULL,
    passed_basic TINYINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME,
    updated_at DATETIME
);

CREATE TABLE riding_record (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    horse_id BIGINT,
    record_date DATE NOT NULL,
    start_time VARCHAR(5) NOT NULL,
    end_time VARCHAR(5) NOT NULL,
    status VARCHAR(16) NOT NULL,
    fee DECIMAL(10,2) NOT NULL,
    times_used INT NOT NULL,
    remark VARCHAR(255),
    created_at DATETIME,
    updated_at DATETIME
);

CREATE TABLE health_event (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_no VARCHAR(32) NOT NULL UNIQUE,
    horse_id BIGINT NOT NULL,
    occurred_at DATETIME NOT NULL,
    severity VARCHAR(16) NOT NULL,
    symptom VARCHAR(500) NOT NULL,
    treatment_advice VARCHAR(500) NOT NULL,
    expected_review_date DATE,
    status VARCHAR(20) NOT NULL,
    registered_version BIGINT NOT NULL DEFAULT 0,
    latest_review_id BIGINT,
    created_by VARCHAR(64) NOT NULL,
    created_role VARCHAR(16) NOT NULL,
    closed_at DATETIME,
    closed_by VARCHAR(64),
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);

CREATE TABLE duty_shift (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    shift_no VARCHAR(32) NOT NULL UNIQUE,
    shift_date DATE NOT NULL,
    leader_name VARCHAR(64) NOT NULL,
    handover_note VARCHAR(1000),
    status VARCHAR(16) NOT NULL,
    item_version BIGINT NOT NULL DEFAULT 0,
    create_request_id VARCHAR(64) UNIQUE,
    started_at DATETIME NOT NULL,
    closed_at DATETIME,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL
);
CREATE INDEX idx_duty_shift_date ON duty_shift (shift_date);

CREATE TABLE duty_seq_lock (
    id BIGINT NOT NULL PRIMARY KEY,
    holder VARCHAR(64),
    updated_at DATETIME
);
INSERT INTO duty_seq_lock (id, holder, updated_at) VALUES (1, NULL, CURRENT_TIMESTAMP);

CREATE TABLE handover_item (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    item_no VARCHAR(32) NOT NULL UNIQUE,
    shift_id BIGINT NOT NULL,
    target_shift_id BIGINT,
    item_type VARCHAR(24) NOT NULL,
    ref_type VARCHAR(24),
    ref_id BIGINT,
    change_kind VARCHAR(24),
    title VARCHAR(200) NOT NULL,
    detail VARCHAR(1000),
    priority VARCHAR(16) NOT NULL,
    owner_name VARCHAR(64),
    status VARCHAR(16) NOT NULL,
    expected_version BIGINT NOT NULL DEFAULT 0,
    accepted_at DATETIME,
    accepted_by VARCHAR(64),
    accepted_note VARCHAR(500),
    delay_reason VARCHAR(500),
    delayed_at DATETIME,
    delayed_by VARCHAR(64),
    request_id VARCHAR(64),
    dedup_shift_id BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'PENDING' THEN shift_id ELSE NULL END),
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    CONSTRAINT uk_item_open_dedup UNIQUE (dedup_shift_id, ref_type, ref_id, change_kind)
);
CREATE INDEX idx_item_target_status ON handover_item (target_shift_id, status);

CREATE TABLE handover_acceptance (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    item_id BIGINT NOT NULL UNIQUE,
    shift_id BIGINT NOT NULL,
    acceptor VARCHAR(64) NOT NULL,
    note VARCHAR(500),
    request_id VARCHAR(64) UNIQUE,
    created_at DATETIME NOT NULL
);

CREATE TABLE handover_item_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    item_id BIGINT NOT NULL,
    action VARCHAR(24) NOT NULL,
    note VARCHAR(500) NOT NULL,
    operator VARCHAR(64) NOT NULL,
    role VARCHAR(16) NOT NULL,
    request_id VARCHAR(64) UNIQUE,
    created_at DATETIME NOT NULL
);
