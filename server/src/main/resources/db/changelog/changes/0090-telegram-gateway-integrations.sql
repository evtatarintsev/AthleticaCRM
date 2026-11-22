--liquibase formatted sql

--changeset dev:0090-telegram-gateway-integrations
-- Подключённый организацией аккаунт Telegram Gateway для отправки одноразовых кодов.
CREATE TABLE telegram_gateway_integrations (
    org_id     UUID          NOT NULL PRIMARY KEY REFERENCES organizations (id),
    token      TEXT          NOT NULL,
    enabled    BOOLEAN       NOT NULL,
    balance    NUMERIC(12,2) NULL,
    balance_at TIMESTAMPTZ   NULL
);
