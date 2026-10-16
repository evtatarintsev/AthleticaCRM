--liquibase formatted sql

--changeset dev:0078-tasks-system-author
-- Задачу может создать система (например, «Проверить клиента» после активации абонемента
-- без участия сотрудника): created_by NULL — автор «Система».
ALTER TABLE tasks ALTER COLUMN created_by DROP NOT NULL;
