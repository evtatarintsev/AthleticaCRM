--liquibase formatted sql

--changeset dev:0073-inbound-receipts

-- Сырое входящее сообщение из внешнего канала до разбора (инбокс).
-- Записывается при приёме (быстрый ack вебхука), затем резолвер привязывает к клиенту либо оставляет
-- UNRESOLVED для ручного разбора (триаж). messages наполняется только после успешного резолва.
CREATE TABLE inbound_receipts (
    id                  UUID        PRIMARY KEY,
    org_id              UUID        NOT NULL,
    integration_id      UUID        NOT NULL REFERENCES channel_integrations (id) ON DELETE CASCADE,
    from_address        TEXT        NOT NULL,
    body                TEXT        NOT NULL,
    state               TEXT        NOT NULL DEFAULT 'UNRESOLVED',
    resolved_message_id UUID,
    received_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Очередь ручного разбора: неразобранные, по организации, от старых к новым.
CREATE INDEX idx_inbound_receipts_unresolved
    ON inbound_receipts (org_id, received_at)
    WHERE state = 'UNRESOLVED';
