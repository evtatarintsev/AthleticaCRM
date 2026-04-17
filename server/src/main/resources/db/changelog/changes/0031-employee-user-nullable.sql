--liquibase formatted sql

--changeset dev:0031-employee-user-nullable
-- Сотрудник может существовать без учётной записи пользователя.
-- user_id заполняется позже при отправке доступа через send-access.
ALTER TABLE employees ALTER COLUMN user_id DROP NOT NULL;

-- Уникальность (user_id, org_id) актуальна только для строк где user_id IS NOT NULL.
ALTER TABLE employees DROP CONSTRAINT employees_user_id_org_id_key;
CREATE UNIQUE INDEX employees_user_id_org_id_key
    ON employees (user_id, org_id)
    WHERE user_id IS NOT NULL;
