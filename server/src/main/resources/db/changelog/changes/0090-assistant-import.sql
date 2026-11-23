--liquibase formatted sql

--changeset dev:0090-assistant-import splitStatements:false
-- Имена вложений восстанавливают ленту после перезагрузки; содержимое файлов не хранится.
ALTER TABLE assistant_messages ADD COLUMN attachments JSONB NOT NULL DEFAULT '[]';

-- Строки, полученные из файла один раз; исправления в следующих сообщениях работают с ними.
CREATE TABLE assistant_import_sources (
    id UUID PRIMARY KEY,
    dialog_id UUID NOT NULL,
    org_id UUID NOT NULL,
    message_id UUID NOT NULL,
    kind TEXT NOT NULL CHECK (kind IN ('table', 'photo')),
    data JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (dialog_id, org_id) REFERENCES assistant_dialogs(id, org_id) ON DELETE CASCADE,
    FOREIGN KEY (message_id, dialog_id, org_id) REFERENCES assistant_messages(id, dialog_id, org_id) ON DELETE CASCADE,
    UNIQUE (id, dialog_id, org_id)
);

CREATE TABLE assistant_import_drafts (
    id UUID PRIMARY KEY,
    dialog_id UUID NOT NULL,
    org_id UUID NOT NULL,
    message_id UUID NOT NULL,
    source_id UUID NOT NULL,
    payload JSONB NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('pending', 'executed', 'superseded')),
    receipt JSONB,
    FOREIGN KEY (dialog_id, org_id) REFERENCES assistant_dialogs(id, org_id) ON DELETE CASCADE,
    FOREIGN KEY (message_id, dialog_id, org_id) REFERENCES assistant_messages(id, dialog_id, org_id) ON DELETE CASCADE,
    FOREIGN KEY (source_id, dialog_id, org_id) REFERENCES assistant_import_sources(id, dialog_id, org_id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX assistant_one_pending_import ON assistant_import_drafts(dialog_id) WHERE status = 'pending';
CREATE INDEX assistant_import_drafts_message ON assistant_import_drafts(message_id);

-- Ожидающая карточка диалога одна на обе таблицы; блокировка диалога сериализует проверку.
CREATE FUNCTION assistant_one_pending_card() RETURNS trigger AS $$
DECLARE
    self JSONB := to_jsonb(NEW);
BEGIN
    IF NEW.status = 'pending' THEN
        PERFORM 1 FROM assistant_dialogs WHERE id = NEW.dialog_id FOR UPDATE;
        IF EXISTS (SELECT 1 FROM assistant_client_drafts WHERE dialog_id = NEW.dialog_id AND status = 'pending' AND client_id::text IS DISTINCT FROM self->>'client_id')
            OR EXISTS (SELECT 1 FROM assistant_import_drafts WHERE dialog_id = NEW.dialog_id AND status = 'pending' AND id::text IS DISTINCT FROM self->>'id') THEN
            RAISE EXCEPTION 'assistant dialog already has a pending card' USING ERRCODE = 'unique_violation', CONSTRAINT = 'assistant_one_pending_card';
        END IF;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER assistant_client_drafts_one_pending BEFORE INSERT OR UPDATE OF status ON assistant_client_drafts
    FOR EACH ROW EXECUTE FUNCTION assistant_one_pending_card();
CREATE TRIGGER assistant_import_drafts_one_pending BEFORE INSERT OR UPDATE OF status ON assistant_import_drafts
    FOR EACH ROW EXECUTE FUNCTION assistant_one_pending_card();
