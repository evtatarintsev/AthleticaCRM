--liquibase formatted sql

--changeset dev:0093-client-app-not-me-task
-- Задача «Проверьте номер телефона», созданная по «Это не я»: не более одной открытой на доступ.
ALTER TABLE client_app_access
    ADD COLUMN not_me_task_id UUID REFERENCES tasks (id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED;
