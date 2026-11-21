--liquibase formatted sql

--changeset dev:0086-employee-access-and-presence
-- Статус доступа сотрудника выводится из user_id, blocked_at и last_seen_at; флаг is_active удаляется.
-- Сотрудники с выданным логином и выключенным is_active считаются заблокированными.
ALTER TABLE employees
    ADD COLUMN blocked_at   TIMESTAMPTZ NULL,
    ADD COLUMN last_seen_at TIMESTAMPTZ NULL;

UPDATE employees SET blocked_at = now() WHERE user_id IS NOT NULL AND NOT is_active;

DROP INDEX one_owner_per_org;
CREATE UNIQUE INDEX one_owner_per_org ON employees (org_id) WHERE is_owner = true;

ALTER TABLE employees DROP COLUMN is_active;

--rollback ALTER TABLE employees ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT true;
--rollback UPDATE employees SET is_active = (user_id IS NOT NULL AND blocked_at IS NULL);
--rollback DROP INDEX one_owner_per_org;
--rollback CREATE UNIQUE INDEX one_owner_per_org ON employees (org_id) WHERE is_owner = true AND is_active = true;
--rollback ALTER TABLE employees DROP COLUMN blocked_at, DROP COLUMN last_seen_at;
