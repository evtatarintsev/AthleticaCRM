--liquibase formatted sql

--changeset dev:0052-client-contacts

CREATE TABLE client_contacts (
    id           UUID        PRIMARY KEY,
    org_id       UUID        NOT NULL,
    client_id    UUID        NOT NULL,
    channel_type TEXT        NOT NULL,
    address      TEXT        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (client_id, channel_type, address)
);

CREATE INDEX idx_client_contacts_client
    ON client_contacts (client_id);

-- Задел под резолв входящих сообщений: адрес отправителя -> клиент.
CREATE INDEX idx_client_contacts_lookup
    ON client_contacts (org_id, channel_type, address);
