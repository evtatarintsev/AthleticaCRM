--liquibase formatted sql

--changeset dev:0060-client-contacts-typed

DROP TABLE IF EXISTS client_contacts;

CREATE TABLE client_contacts (
    id         UUID        PRIMARY KEY,
    org_id     UUID        NOT NULL,
    client_id  UUID        NOT NULL,
    type       TEXT        NOT NULL,
    value      TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_client_contacts_client
    ON client_contacts (client_id);

-- Задел под резолв входящих сообщений: значение контакта -> клиент.
CREATE INDEX idx_client_contacts_lookup
    ON client_contacts (org_id, type, value);
