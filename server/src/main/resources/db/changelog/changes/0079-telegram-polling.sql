--liquibase formatted sql

--changeset dev:0079-telegram-polling

-- Позиция опроса Telegram Bot API (getUpdates) и аренда опроса по интеграции.
-- update_offset — следующий ожидаемый update_id; сдвигается в одной транзакции с записью входящих.
-- lease_owner/lease_until — какой экземпляр сервера сейчас опрашивает бота; истёкшую аренду
-- подхватывает другой экземпляр. bot_id — числовой id бота из токена: при смене бота offset сбрасывается.
CREATE TABLE telegram_poll_state (
    integration_id UUID        PRIMARY KEY REFERENCES channel_integrations (id) ON DELETE CASCADE,
    bot_id         TEXT        NOT NULL,
    update_offset  BIGINT      NOT NULL DEFAULT 0,
    lease_owner    TEXT,
    lease_until    TIMESTAMPTZ
);

-- Собеседник в связанном канале (чат Telegram и т.п.): техническая информация о чате,
-- в контакты клиента не попадает. phone — номер, присланный самим собеседником.
CREATE TABLE channel_chats (
    id             UUID        PRIMARY KEY,
    org_id         UUID        NOT NULL,
    integration_id UUID        NOT NULL REFERENCES channel_integrations (id) ON DELETE CASCADE,
    address        TEXT        NOT NULL,
    display_name   TEXT        NOT NULL DEFAULT '',
    username       TEXT,
    phone          TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (integration_id, address)
);

-- Момент последнего входящего по связке: ответ клиенту с несколькими чатами уходит в последний активный.
ALTER TABLE channel_bindings ADD COLUMN last_inbound_at TIMESTAMPTZ;

-- Один бот — одна интеграция во всей системе.
CREATE UNIQUE INDEX uq_channel_integrations_telegram_token
    ON channel_integrations ((config ->> 'botToken'))
    WHERE config ->> 'type' = 'telegram_bot';

-- Очередь разбора группирует неразобранные входящие по чату.
CREATE INDEX idx_inbound_receipts_unresolved_chat
    ON inbound_receipts (integration_id, from_address, received_at)
    WHERE state = 'UNRESOLVED';
