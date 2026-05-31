--liquibase formatted sql

--changeset dev:0054-conversations

-- Один диалог на клиента (кросс-канально); фильтрация по каналу — на стороне UI.
CREATE TABLE conversations (
    id              UUID        PRIMARY KEY,
    org_id          UUID        NOT NULL,
    client_id       UUID        NOT NULL,
    last_message_at TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (org_id, client_id)
);
