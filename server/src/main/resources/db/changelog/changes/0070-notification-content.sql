--liquibase formatted sql

--changeset dev:0070-notification-content
DELETE FROM notifications;

ALTER TABLE notifications
    DROP COLUMN title,
    DROP COLUMN body,
    ADD COLUMN content JSONB NOT NULL;
