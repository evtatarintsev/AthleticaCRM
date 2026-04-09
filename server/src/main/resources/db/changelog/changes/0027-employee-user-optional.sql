--liquibase formatted sql

--changeset dev:0027-employee-user-optional
-- Сотрудник может существовать без учётной записи (добавлен вручную администратором).
-- user_id остаётся NOT NULL только у владельца — он создаётся через sign-up.
ALTER TABLE employees ALTER COLUMN user_id DROP NOT NULL;

-- Уникальность (user_id, org_id) сохраняем только для строк где user_id IS NOT NULL.
ALTER TABLE employees DROP CONSTRAINT employees_user_id_org_id_key;
CREATE UNIQUE INDEX employees_user_id_org_id_key
    ON employees (user_id, org_id)
    WHERE user_id IS NOT NULL;
