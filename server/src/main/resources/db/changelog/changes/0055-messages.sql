--liquibase formatted sql

--changeset dev:0055-messages

CREATE TABLE messages (
    id                     UUID        PRIMARY KEY,
    org_id                 UUID        NOT NULL,
    conversation_id        UUID        NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    channel_integration_id UUID,
    channel_type           TEXT        NOT NULL,
    direction              TEXT        NOT NULL,
    sender_kind            TEXT        NOT NULL,
    sender_employee_id     UUID,
    recipient_address      TEXT,
    body                   TEXT        NOT NULL,
    status                 TEXT        NOT NULL,
    provider_message_ref   TEXT,
    error_code             TEXT,
    error_message          TEXT,
    retry_count            INT         NOT NULL DEFAULT 0,
    broadcast_id           UUID,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at                TIMESTAMPTZ,
    delivered_at           TIMESTAMPTZ,
    read_at                TIMESTAMPTZ
);

CREATE INDEX idx_messages_conversation
    ON messages (conversation_id, created_at);

-- Поллинг диспетчером исходящих сообщений в очереди.
CREATE INDEX idx_messages_pending
    ON messages (created_at)
    WHERE status = 'QUEUED' AND direction = 'OUTBOUND';

-- Задел под матчинг квитанций о доставке (delivery receipt) по id у провайдера.
CREATE INDEX idx_messages_provider_ref
    ON messages (provider_message_ref)
    WHERE provider_message_ref IS NOT NULL;
