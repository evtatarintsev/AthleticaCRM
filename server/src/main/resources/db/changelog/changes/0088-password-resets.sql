--liquibase formatted sql

--changeset dev:0088-password-resets
-- Ссылка сброса пароля: одна на пользователя, новая перезаписывает прежнюю; токен хранится только хэшем.
CREATE TABLE password_resets (
    user_id UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    last_sent_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ
);

--rollback DROP TABLE password_resets;
