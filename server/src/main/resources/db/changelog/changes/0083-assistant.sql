--liquibase formatted sql

--changeset dev:0083-assistant
-- Составные ключи запрещают связывать личные диалоги и карточки разных организаций.
ALTER TABLE employees ADD CONSTRAINT employees_id_org_unique UNIQUE (id, org_id);

CREATE TABLE assistant_settings (
    org_id UUID PRIMARY KEY REFERENCES organizations(id) ON DELETE CASCADE,
    enabled BOOLEAN NOT NULL DEFAULT false,
    daily_message_limit INTEGER NOT NULL DEFAULT 100 CHECK (daily_message_limit > 0)
);
CREATE TABLE assistant_usage (
    org_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    local_date DATE NOT NULL,
    accepted_messages INTEGER NOT NULL DEFAULT 0 CHECK (accepted_messages >= 0),
    PRIMARY KEY (org_id, local_date)
);
CREATE TABLE assistant_dialogs (
    id UUID PRIMARY KEY,
    org_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    employee_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (employee_id, org_id) REFERENCES employees(id, org_id) ON DELETE CASCADE,
    UNIQUE (id, org_id)
);
CREATE INDEX assistant_dialogs_current ON assistant_dialogs(org_id, employee_id, created_at DESC, id DESC);
CREATE TABLE assistant_messages (
    id UUID PRIMARY KEY,
    dialog_id UUID NOT NULL,
    org_id UUID NOT NULL,
    position BIGSERIAL NOT NULL,
    role TEXT NOT NULL CHECK (role IN ('user', 'assistant')),
    text TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL CHECK (status IN ('processing', 'completed', 'failed')),
    deadline TIMESTAMPTZ NOT NULL,
    blocks JSONB NOT NULL DEFAULT '[]',
    provider_history JSONB NOT NULL DEFAULT '[]',
    error_code TEXT,
    FOREIGN KEY (dialog_id, org_id) REFERENCES assistant_dialogs(id, org_id) ON DELETE CASCADE,
    UNIQUE (id, dialog_id, org_id),
    UNIQUE (dialog_id, position)
);
CREATE UNIQUE INDEX assistant_one_processing ON assistant_messages(dialog_id) WHERE status = 'processing';
CREATE TABLE assistant_client_drafts (
    client_id UUID PRIMARY KEY,
    dialog_id UUID NOT NULL,
    org_id UUID NOT NULL,
    message_id UUID NOT NULL,
    payload JSONB NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('pending', 'executed', 'superseded')),
    FOREIGN KEY (dialog_id, org_id) REFERENCES assistant_dialogs(id, org_id) ON DELETE CASCADE,
    FOREIGN KEY (message_id, dialog_id, org_id) REFERENCES assistant_messages(id, dialog_id, org_id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX assistant_one_pending_draft ON assistant_client_drafts(dialog_id) WHERE status = 'pending';
CREATE INDEX assistant_drafts_message ON assistant_client_drafts(message_id);
