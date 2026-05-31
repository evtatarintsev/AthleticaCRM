--liquibase formatted sql

--changeset dev:0053-channel-integrations

-- TODO: config хранит секреты провайдеров (токены, API-ключи) открытым текстом.
-- Зашифровать (app-level AES-GCM) отдельной задачей.
CREATE TABLE channel_integrations (
    id           UUID        PRIMARY KEY,
    org_id       UUID        NOT NULL,
    channel_type TEXT        NOT NULL,
    name         TEXT        NOT NULL,
    config       JSONB       NOT NULL DEFAULT '{}'::jsonb,
    enabled      BOOLEAN     NOT NULL DEFAULT true,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_channel_integrations_org
    ON channel_integrations (org_id);
