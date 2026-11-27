--liquibase formatted sql

--changeset dev:0094-client-app-absence-notices
-- Предупреждения «Не придём» из кабинета клиента: одно на клиента в занятии, отмена удаляет строку.
CREATE TABLE client_app_absence_notices
(
    id         UUID        NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    session_id UUID        NOT NULL REFERENCES sessions (id) ON DELETE CASCADE,
    client_id  UUID        NOT NULL REFERENCES clients (id) ON DELETE CASCADE,
    access_id  UUID        NOT NULL REFERENCES client_app_access (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX uq_client_app_absence_notices_session_client ON client_app_absence_notices (session_id, client_id);

CREATE INDEX idx_client_app_absence_notices_client ON client_app_absence_notices (client_id);
