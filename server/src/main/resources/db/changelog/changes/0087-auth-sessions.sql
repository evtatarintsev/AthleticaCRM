--liquibase formatted sql

--changeset dev:0087-auth-sessions
-- Серверные сессии входа: отзыв сессии — удаление строки, токены без живой сессии не принимаются.
CREATE TABLE auth_sessions
(
    id               UUID PRIMARY KEY,
    user_id          UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    org_id           UUID        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    created_at       TIMESTAMPTZ NOT NULL,
    last_seen_at     TIMESTAMPTZ NOT NULL,
    user_agent       TEXT        NULL,
    ip               TEXT        NULL,
    refresh_jti      UUID        NOT NULL,
    prev_refresh_jti UUID        NULL,
    rotated_at       TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_auth_sessions_user_id ON auth_sessions (user_id);

-- У пользователя не больше одного сотрудника.
DROP INDEX employees_user_id_org_id_key;
CREATE UNIQUE INDEX employees_user_id_key ON employees (user_id) WHERE user_id IS NOT NULL;

--rollback DROP INDEX employees_user_id_key;
--rollback CREATE UNIQUE INDEX employees_user_id_org_id_key ON employees (user_id, org_id) WHERE user_id IS NOT NULL;
--rollback DROP TABLE auth_sessions;
