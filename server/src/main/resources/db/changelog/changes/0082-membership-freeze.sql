--liquibase formatted sql

--changeset dev:0082-membership-freeze
-- Текущая (запланированная или действующая) заморозка абонемента. Прошлые заморозки — только в истории.
-- Даты и причина заполняются вместе; оформивший сотрудник обнуляется при его удалении, как issued_by.
ALTER TABLE memberships
    ADD COLUMN freeze_start  DATE,
    ADD COLUMN freeze_end    DATE,
    ADD COLUMN freeze_reason TEXT,
    ADD COLUMN freeze_by     UUID REFERENCES employees (id) ON DELETE SET NULL,
    ADD CONSTRAINT chk_memberships_freeze CHECK (
        (freeze_start IS NULL AND freeze_end IS NULL AND freeze_reason IS NULL AND freeze_by IS NULL)
        OR (freeze_start IS NOT NULL AND freeze_end IS NOT NULL AND freeze_reason IS NOT NULL
            AND freeze_end >= freeze_start AND state = 'ACTIVE')
    );
