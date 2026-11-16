--liquibase formatted sql

--changeset dev:0083-client-card-sections
CREATE TABLE client_card_sections (
    org_id UUID PRIMARY KEY REFERENCES organizations (id) ON DELETE CASCADE,
    tasks BOOLEAN NOT NULL,
    notes BOOLEAN NOT NULL,
    chat BOOLEAN NOT NULL,
    documents BOOLEAN NOT NULL
);

-- Сохраняем видимость разделов существующих организаций; новые используют простой набор.
INSERT INTO client_card_sections (org_id, tasks, notes, chat, documents)
SELECT id, true, true, true, true FROM organizations;
