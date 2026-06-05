--liquibase formatted sql

--changeset dev:0057-conversation-read-state

-- Read horizon диалога: указатель «последнее прочитанное сотрудниками».
-- Непрочитанность считается относительно last_read_at, без per-message флагов.
CREATE TABLE conversation_read_state (
    conversation_id UUID        PRIMARY KEY REFERENCES conversations (id) ON DELETE CASCADE,
    org_id          UUID        NOT NULL,
    last_read_at    TIMESTAMPTZ NOT NULL
);
