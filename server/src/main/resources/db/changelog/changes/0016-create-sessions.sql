--liquibase formatted sql

--changeset dev:0016-create-sessions

CREATE TYPE session_status AS ENUM (
    'scheduled',
    'completed',
    'cancelled'
);

CREATE TABLE sessions (
    id         UUID           NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    org_id     UUID           NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    group_id   UUID           REFERENCES groups (id) ON DELETE SET NULL,
    date       DATE           NOT NULL,
    start_time TIME           NOT NULL,
    end_time   TIME           NOT NULL,
    status     session_status NOT NULL DEFAULT 'scheduled',
    is_manual  BOOLEAN        NOT NULL DEFAULT false,
    notes      TEXT,
    created_at TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT check_times CHECK (end_time > start_time)
);

CREATE INDEX idx_sessions_org_date ON sessions (org_id, date);
CREATE INDEX idx_sessions_group ON sessions (group_id);
CREATE INDEX idx_sessions_status ON sessions (org_id, status);
