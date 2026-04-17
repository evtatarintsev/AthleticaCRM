--liquibase formatted sql

--changeset dev:0032-create-org-emails
-- Исходящие письма организации. Сохраняются в той же транзакции что и бизнес-операция,
-- фоновый воркер читает PENDING-строки и отправляет через SMTP.
CREATE TABLE org_emails (
    id            UUID        NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    org_id        UUID        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    to_addresses  TEXT        NOT NULL, -- comma-separated, на практике один адрес
    subject       TEXT        NOT NULL,
    text_body     TEXT        NOT NULL,
    html_body     TEXT        NOT NULL,
    status        VARCHAR(10) NOT NULL DEFAULT 'PENDING', -- PENDING | SENT | FAILED
    attempt_count INT         NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at       TIMESTAMPTZ,
    last_error    TEXT
);

CREATE INDEX idx_org_emails_pending ON org_emails (created_at)
    WHERE status = 'PENDING';
