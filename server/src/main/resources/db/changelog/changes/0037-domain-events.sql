--liquibase formatted sql

--changeset dev:0037-domain-events

CREATE TABLE domain_events (
    id           UUID        PRIMARY KEY,
    org_id       UUID        NOT NULL,
    event_type   TEXT        NOT NULL,
    payload      JSONB       NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at TIMESTAMPTZ,
    retry_count  INT         NOT NULL DEFAULT 0,
    last_error   TEXT
);

CREATE INDEX idx_domain_events_pending
    ON domain_events (created_at)
    WHERE processed_at IS NULL AND retry_count < 5;
