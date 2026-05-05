--liquibase formatted sql
--changeset dev:0045-user-display-settings

CREATE TABLE user_display_settings
(
    user_id  UUID  NOT NULL REFERENCES users (id) ON DELETE CASCADE PRIMARY KEY,
    settings JSONB NOT NULL DEFAULT '{}'
);