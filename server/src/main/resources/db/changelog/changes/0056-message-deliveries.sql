--liquibase formatted sql

--changeset dev:0056-message-deliveries

-- Доставка сообщения в конкретный канал. Одно сообщение -> N доставок (фан-аут).
-- state: PENDING | SENT | DELIVERED | FAILED; error_* заполняются только при FAILED.
-- recipient_address всегда задан: для IN_APP это идентификатор клиента строкой.
CREATE TABLE message_deliveries (
    id                     UUID        PRIMARY KEY,
    org_id                 UUID        NOT NULL,
    message_id             UUID        NOT NULL REFERENCES messages (id) ON DELETE CASCADE,
    channel_integration_id UUID        NOT NULL REFERENCES channel_integrations (id),
    recipient_address      TEXT        NOT NULL,
    state                  TEXT        NOT NULL DEFAULT 'PENDING',
    provider_message_ref   TEXT,
    error_code             TEXT,
    error_message          TEXT,
    attempts               INT         NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at                TIMESTAMPTZ,
    delivered_at           TIMESTAMPTZ
);

-- Поллинг диспетчером доставок в очереди.
CREATE INDEX idx_deliveries_pending
    ON message_deliveries (created_at)
    WHERE state = 'PENDING';

CREATE INDEX idx_deliveries_message
    ON message_deliveries (message_id);

-- Задел под матчинг квитанций о доставке (delivery receipt) по id у провайдера.
CREATE INDEX idx_deliveries_provider_ref
    ON message_deliveries (provider_message_ref)
    WHERE provider_message_ref IS NOT NULL;
