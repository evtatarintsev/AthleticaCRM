--liquibase formatted sql

--changeset dev:0067-session-attendance
CREATE TYPE attendance_kind AS ENUM ('regular', 'one_time');

CREATE TYPE attendance_presence AS ENUM ('present', 'absent');

CREATE TABLE session_attendance
(
    id         UUID            NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    session_id UUID            NOT NULL REFERENCES sessions (id) ON DELETE CASCADE,
    client_id  UUID            NOT NULL REFERENCES clients (id) ON DELETE CASCADE,
    kind       attendance_kind NOT NULL,
    presence   attendance_presence,
    marked_by  UUID            REFERENCES employees (id) ON DELETE SET NULL,
    marked_at  TIMESTAMPTZ,
    created_at TIMESTAMPTZ     NOT NULL DEFAULT now()
);

-- Уникальный индекс начинается с session_id, поэтому служит и индексом по занятию:
-- чтение журнала и проверка «занятие изменено человеком» идут по нему.
CREATE UNIQUE INDEX uq_session_attendance_session_client ON session_attendance (session_id, client_id);

CREATE INDEX idx_session_attendance_client ON session_attendance (client_id);
