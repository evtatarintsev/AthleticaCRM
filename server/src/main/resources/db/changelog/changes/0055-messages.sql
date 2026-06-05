--liquibase formatted sql

--changeset dev:0055-messages

-- Сообщение — канал-агностичный контент. Механика отправки вынесена в message_deliveries.
-- direction различает исходящие (author_*) и входящие (received_via).
CREATE TABLE messages (
    id                 UUID        PRIMARY KEY,
    org_id             UUID        NOT NULL,
    conversation_id    UUID        NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    direction          TEXT        NOT NULL,
    body               TEXT        NOT NULL,
    author_kind        TEXT,
    author_employee_id UUID,
    received_via       UUID,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_messages_conversation
    ON messages (conversation_id, created_at);
