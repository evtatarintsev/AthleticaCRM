--liquibase formatted sql

--changeset dev:0074-memberships-wipe
-- Одноразово, до продакшена: реальных абонементов нет, состояния и режимы для старых строк
-- не вычисляются. Не повторять в следующих миграциях.
DELETE FROM memberships;

--changeset dev:0074-membership-lifecycle
-- Жизненный цикл абонемента: сохранённое состояние, режимы активации и закрытия, срок для
-- вычисления даты окончания при активации. До активации дат начала и окончания нет.
ALTER TABLE memberships
    ALTER COLUMN start_date DROP NOT NULL,
    ALTER COLUMN end_date DROP NOT NULL,
    ADD COLUMN state               VARCHAR(16) NOT NULL,
    ADD COLUMN activation_mode     VARCHAR(16) NOT NULL,
    ADD COLUMN activate_on         DATE,
    ADD COLUMN activation_deadline DATE,
    ADD COLUMN duration_value      INT         NOT NULL,
    ADD COLUMN duration_unit       VARCHAR(16) NOT NULL,
    ADD COLUMN close_mode          VARCHAR(16) NOT NULL,
    ADD COLUMN issued_on           DATE        NOT NULL,
    ADD COLUMN close_notified_at   TIMESTAMPTZ,
    ADD COLUMN closed_on           DATE,
    ADD COLUMN close_reason        VARCHAR(16),
    ADD CONSTRAINT chk_memberships_state
        CHECK (state IN ('INACTIVE', 'ACTIVE', 'CLOSED', 'REVOKED')),
    ADD CONSTRAINT chk_memberships_activation_mode
        CHECK (activation_mode IN ('IMMEDIATE', 'ON_DATE', 'ON_FIRST_VISIT')),
    ADD CONSTRAINT chk_memberships_close_mode
        CHECK (close_mode IN ('MANUAL', 'ON_LAST_SESSION', 'ON_EXPIRY')),
    ADD CONSTRAINT chk_memberships_close_reason
        CHECK (close_reason IS NULL OR close_reason IN ('MANUAL', 'LAST_SESSION', 'EXPIRY')),
    ADD CONSTRAINT chk_memberships_active_dates
        CHECK (state NOT IN ('ACTIVE', 'CLOSED') OR (start_date IS NOT NULL AND end_date IS NOT NULL)),
    ADD CONSTRAINT chk_memberships_inactive_dates
        CHECK (state <> 'INACTIVE' OR (start_date IS NULL AND end_date IS NULL)),
    ADD CONSTRAINT chk_memberships_closed
        CHECK ((state = 'CLOSED') = (closed_on IS NOT NULL AND close_reason IS NOT NULL)),
    ADD CONSTRAINT chk_memberships_on_date
        CHECK (activation_mode <> 'ON_DATE' OR activate_on IS NOT NULL),
    ADD CONSTRAINT chk_memberships_last_session
        CHECK (close_mode <> 'ON_LAST_SESSION' OR sessions_total IS NOT NULL);

-- Выборка фоновой обработки: нефинальные абонементы.
CREATE INDEX idx_memberships_pending ON memberships (state) WHERE state IN ('INACTIVE', 'ACTIVE');

-- История абонемента: каждое действие и переход, пишется в транзакции изменения.
-- actor_id NULL — действие системы; FK нет, сотрудники удаляются мягко (employees.is_active).
CREATE TABLE membership_events
(
    id            UUID        NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    org_id        UUID        NOT NULL REFERENCES organizations (id),
    membership_id UUID        NOT NULL REFERENCES memberships (id) ON DELETE CASCADE,
    occurred_at   TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    actor_id      UUID,
    kind          VARCHAR(32) NOT NULL,
    payload       JSONB       NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX idx_membership_events_membership ON membership_events (membership_id, occurred_at DESC);
