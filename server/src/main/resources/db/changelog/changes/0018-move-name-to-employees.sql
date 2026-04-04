--liquibase formatted sql

--changeset dev:0018-move-name-to-employees
ALTER TABLE employees ADD COLUMN name VARCHAR(255) NOT NULL DEFAULT '';
UPDATE employees e SET name = u.name FROM users u WHERE e.user_id = u.id;
ALTER TABLE employees ALTER COLUMN name DROP DEFAULT;
ALTER TABLE users DROP COLUMN name;
