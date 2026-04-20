-- Chronicle v2.0 — Database Schema
-- Run once on first boot (DatabaseManager handles this automatically)

CREATE TABLE IF NOT EXISTS chronicle_reports (
    id              BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    accused_uuid    CHAR(36)            NOT NULL,
    accused_name    VARCHAR(16)         NOT NULL,
    reporter_uuid   CHAR(36)            NOT NULL,
    reporter_name   VARCHAR(16)         NOT NULL,
    reason          TEXT,
    world           VARCHAR(64)         NOT NULL,
    pos_x           DOUBLE              NOT NULL,
    pos_y           DOUBLE              NOT NULL,
    pos_z           DOUBLE              NOT NULL,
    tdf_blob        LONGBLOB,         
    created_at      DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_by     CHAR(36),
    reviewed_at     DATETIME,
    status          ENUM('OPEN','REVIEWING','CLOSED') NOT NULL DEFAULT 'OPEN',
    PRIMARY KEY (id),
    INDEX idx_accused  (accused_uuid),
    INDEX idx_status   (status),
    INDEX idx_created  (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS chronicle_snapshots (
    id              BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    triggered_by    CHAR(36)            NOT NULL,
    server_id       VARCHAR(64)         NOT NULL DEFAULT 'default',
    label           VARCHAR(128),
    entity_count    INT UNSIGNED        NOT NULL DEFAULT 0,
    block_count     INT UNSIGNED        NOT NULL DEFAULT 0,
    data_blob       LONGBLOB,          
    created_at      DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS chronicle_live_sessions (
    id              BIGINT UNSIGNED     NOT NULL AUTO_INCREMENT,
    watcher_uuid    CHAR(36)            NOT NULL,
    target_uuid     CHAR(36)            NOT NULL,
    started_at      DATETIME            NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at        DATETIME,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
