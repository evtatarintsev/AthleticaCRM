--liquibase formatted sql

--changeset dev:0092-client-app
-- Кабинет клиента: настройка организации, аккаунты по номеру, их сеансы, коды входа и доступы.
ALTER TABLE organizations
    ADD COLUMN client_app_enabled BOOLEAN NOT NULL DEFAULT false;

-- Аккаунт кабинета — один на номер во всех организациях. У удалённого аккаунта номер обезличен.
CREATE TABLE client_app_accounts (
    id                UUID PRIMARY KEY,
    phone_digits      TEXT        NULL UNIQUE,
    created_at        TIMESTAMPTZ NOT NULL,
    terms_accepted_at TIMESTAMPTZ NULL,
    terms_version     TEXT        NULL,
    deleted_at        TIMESTAMPTZ NULL,
    CONSTRAINT ck_client_app_accounts_phone CHECK ((deleted_at IS NULL) = (phone_digits IS NOT NULL)),
    CONSTRAINT ck_client_app_accounts_terms CHECK ((terms_accepted_at IS NULL) = (terms_version IS NULL))
);

-- Сеанс аккаунта в браузере или на устройстве; ротация refresh — как у auth_sessions.
CREATE TABLE client_app_sessions (
    id               UUID PRIMARY KEY,
    account_id       UUID        NOT NULL REFERENCES client_app_accounts (id) ON DELETE CASCADE,
    platform         TEXT        NOT NULL,
    name             TEXT        NULL,
    created_at       TIMESTAMPTZ NOT NULL,
    last_seen_at     TIMESTAMPTZ NOT NULL,
    refresh_jti      UUID        NOT NULL,
    prev_refresh_jti UUID        NULL,
    rotated_at       TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_client_app_sessions_account_id ON client_app_sessions (account_id);

-- Одноразовый код входа. Строка пишется на каждый запрос кода, в том числе для постороннего номера:
-- по ней считаются лимиты. channel = NULL — код не отправлялся.
CREATE TABLE client_app_codes (
    id           UUID PRIMARY KEY,
    phone_digits TEXT        NOT NULL,
    code_hash    TEXT        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    expires_at   TIMESTAMPTZ NOT NULL,
    attempts     INT         NOT NULL DEFAULT 0,
    channel      TEXT        NULL,
    used_at      TIMESTAMPTZ NULL,
    CONSTRAINT ck_client_app_codes_channel CHECK (channel IN ('TELEGRAM', 'SMS'))
);

CREATE INDEX idx_client_app_codes_phone_created ON client_app_codes (phone_digits, created_at);

-- Доступ к кабинету по паре «клиент — номер телефона из его контактов».
-- Нет строки — номер не приглашён; строка появляется только приглашением.
CREATE TABLE client_app_access (
    id                UUID PRIMARY KEY,
    org_id            UUID        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    client_id         UUID        NOT NULL REFERENCES clients (id) ON DELETE CASCADE,
    phone_digits      TEXT        NOT NULL,
    status            TEXT        NOT NULL,
    invited_at        TIMESTAMPTZ NOT NULL,
    invite_channel    TEXT        NULL,
    status_changed_at TIMESTAMPTZ NOT NULL,
    last_seen_at      TIMESTAMPTZ NULL,
    CONSTRAINT uq_client_app_access_org_client_phone UNIQUE (org_id, client_id, phone_digits),
    CONSTRAINT ck_client_app_access_status CHECK (status IN ('INVITED', 'CONNECTED', 'NOT_ME', 'DISABLED'))
);

CREATE INDEX idx_client_app_access_phone ON client_app_access (phone_digits);

-- Номер телефона в виде для сравнения — то же правило, что PhoneDigits в коде.
CREATE FUNCTION normalized_phone(raw TEXT) RETURNS TEXT
    LANGUAGE sql IMMUTABLE STRICT AS
$$
SELECT CASE
           WHEN length(d) = 11 AND left(d, 1) = '8' THEN '7' || substr(d, 2)
           ELSE d
       END
FROM (SELECT regexp_replace(raw, '[^0-9]', '', 'g') AS d) digits
$$;

--rollback DROP FUNCTION normalized_phone(TEXT);
--rollback DROP TABLE client_app_access;
--rollback DROP TABLE client_app_codes;
--rollback DROP TABLE client_app_sessions;
--rollback DROP TABLE client_app_accounts;
--rollback ALTER TABLE organizations DROP COLUMN client_app_enabled;
