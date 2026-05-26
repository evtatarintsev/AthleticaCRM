--liquibase formatted sql

--changeset dev:0051-create-client-notes
CREATE TABLE client_notes (
    id         UUID PRIMARY KEY DEFAULT uuidv7(),
    org_id     UUID        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    client_id  UUID        NOT NULL REFERENCES clients (id) ON DELETE CASCADE,
    author_id  UUID        NOT NULL REFERENCES employees (id) ON DELETE RESTRICT,
    text       TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ
);

CREATE INDEX idx_client_notes_client
    ON client_notes (client_id, created_at DESC)
    WHERE deleted_at IS NULL;
