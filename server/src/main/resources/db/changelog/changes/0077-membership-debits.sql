--liquibase formatted sql

--changeset dev:0077-membership-debits
-- Списания занятий с абонементов. Возврат не удаляет запись, а ставит reversed_at.
-- Прошлая посещаемость без списаний становится неоплаченной — данных до продакшена нет.
CREATE TABLE membership_debits
(
    id            UUID        NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    org_id        UUID        NOT NULL REFERENCES organizations (id),
    membership_id UUID        NOT NULL REFERENCES memberships (id) ON DELETE CASCADE,
    client_id     UUID        NOT NULL REFERENCES clients (id) ON DELETE CASCADE,
    session_id    UUID        NOT NULL REFERENCES sessions (id) ON DELETE CASCADE,
    kind          VARCHAR(16) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    reversed_at   TIMESTAMPTZ,
    CONSTRAINT chk_membership_debits_kind CHECK (kind IN ('REGULAR', 'DEBT'))
);

-- Не больше одного действующего списания за участие клиента в занятии.
CREATE UNIQUE INDEX uq_membership_debits_active
    ON membership_debits (session_id, client_id)
    WHERE reversed_at IS NULL;

CREATE INDEX idx_membership_debits_membership ON membership_debits (membership_id);
