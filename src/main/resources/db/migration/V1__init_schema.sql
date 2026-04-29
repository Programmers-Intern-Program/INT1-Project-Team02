-- V1: base schema before embedding search features.
-- Existing databases that adopt Flyway start from this baseline version.
-- Fresh databases run this file, then later versioned migrations.

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS discord_servers (
    id         BIGSERIAL    PRIMARY KEY,
    guild_id   VARCHAR(50)  NOT NULL UNIQUE,
    guild_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS api_keys (
    id          BIGSERIAL    PRIMARY KEY,
    server_id   BIGINT       NOT NULL REFERENCES discord_servers (id),
    key_hash    VARCHAR(255) NOT NULL,
    description VARCHAR(100),
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS projects (
    id          BIGSERIAL    PRIMARY KEY,
    server_id   BIGINT       REFERENCES discord_servers (id) ON DELETE SET NULL,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    tech_stack  VARCHAR(255),
    metadata    JSONB,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS meetings (
    id         BIGSERIAL   PRIMARY KEY,
    project_id BIGINT      REFERENCES projects (id) ON DELETE SET NULL,
    title      VARCHAR(200),
    started_at TIMESTAMP   NOT NULL DEFAULT NOW(),
    ended_at   TIMESTAMP,
    status     VARCHAR(20) NOT NULL
);

CREATE TABLE IF NOT EXISTS utterances (
    id                 BIGSERIAL    PRIMARY KEY,
    meeting_id         BIGINT       NOT NULL REFERENCES meetings (id),
    speaker_name       VARCHAR(100) NOT NULL,
    speaker_discord_id VARCHAR(50)  NOT NULL,
    content            TEXT         NOT NULL,
    spoken_at          TIMESTAMP    NOT NULL
);

CREATE TABLE IF NOT EXISTS context_cache (
    id              BIGSERIAL   PRIMARY KEY,
    meeting_id      BIGINT      NOT NULL REFERENCES meetings (id),
    compressed_text TEXT        NOT NULL,
    token_count     INTEGER     NOT NULL,
    turn_range      VARCHAR(50) NOT NULL,
    created_at      TIMESTAMP   NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS meeting_summaries (
    id               BIGSERIAL PRIMARY KEY,
    meeting_id       BIGINT    NOT NULL REFERENCES meetings (id) UNIQUE,
    summary          TEXT      NOT NULL,
    unresolved_items TEXT,
    is_confirmed     BOOLEAN   NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS decisions (
    id         BIGSERIAL PRIMARY KEY,
    project_id BIGINT    NOT NULL REFERENCES projects (id),
    meeting_id BIGINT    REFERENCES meetings (id) ON DELETE SET NULL,
    content    TEXT      NOT NULL,
    embedding  VECTOR(768),
    decided_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS work_logs (
    id            BIGSERIAL    PRIMARY KEY,
    meeting_id    BIGINT       NOT NULL REFERENCES meetings (id),
    project_id    BIGINT       NOT NULL REFERENCES projects (id),
    assignee_name VARCHAR(100) NOT NULL,
    task          TEXT         NOT NULL,
    due_date      DATE,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);
