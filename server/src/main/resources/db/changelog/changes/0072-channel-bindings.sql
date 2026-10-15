--liquibase formatted sql

--changeset dev:0072-channel-bindings

-- Связка «канальный адрес ↔ клиент» в рамках интеграции.
-- Нужна связанным каналам (Telegram/VK), где адрес отправки (chat_id/vk_id) устанавливается в рантайме
-- и не совпадает с человеко-понятным контактом. Используется в обе стороны: приём (адрес → клиент) и
-- отправка (клиент → адрес). address непрозрачен — строка в адресном пространстве канала.
CREATE TABLE channel_bindings (
    id             UUID        PRIMARY KEY,
    org_id         UUID        NOT NULL,
    integration_id UUID        NOT NULL REFERENCES channel_integrations (id) ON DELETE CASCADE,
    client_id      UUID        NOT NULL REFERENCES clients (id) ON DELETE CASCADE,
    address        TEXT        NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (integration_id, address)
);

CREATE INDEX idx_channel_bindings_client
    ON channel_bindings (integration_id, client_id);
