--liquibase formatted sql

--changeset dev:0028-employee-user-required
-- Откат 0027: каждый сотрудник должен иметь учётную запись пользователя.
-- Удаляем сотрудников без user_id (добавленных во время действия 0027).
DELETE FROM employees WHERE user_id IS NULL;

-- Восстанавливаем NOT NULL на user_id.
ALTER TABLE employees ALTER COLUMN user_id SET NOT NULL;

-- Удаляем частичный уникальный индекс из 0027 и восстанавливаем оригинальный constraint.
DROP INDEX IF EXISTS employees_user_id_org_id_key;
ALTER TABLE employees ADD CONSTRAINT employees_user_id_org_id_key UNIQUE (user_id, org_id);
