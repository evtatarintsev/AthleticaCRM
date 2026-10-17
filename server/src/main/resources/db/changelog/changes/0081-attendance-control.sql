--liquibase formatted sql

--changeset dev:0081-attendance-control

-- Настройки контроля посещаемости организации. Строки нет — действуют значения по умолчанию:
-- задача создаётся через 120 минут после окончания незакрытого занятия.
CREATE TABLE attendance_control_settings (
    org_id                     UUID        PRIMARY KEY REFERENCES organizations (id) ON DELETE CASCADE,
    overdue_task_enabled       BOOLEAN     NOT NULL,
    overdue_task_delay_minutes INTEGER     NOT NULL CHECK (overdue_task_delay_minutes BETWEEN 15 AND 10080),
    updated_at                 TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Задача «Закрыть журнал» создаётся по занятию не больше одного раза. Первичный ключ — гард от
-- повторного создания при параллельных проходах; ссылка на задачу отложена до конца транзакции,
-- чтобы занять занятие раньше, чем создавать задачу.
CREATE TABLE session_overdue_tasks (
    session_id UUID        PRIMARY KEY REFERENCES sessions (id) ON DELETE CASCADE,
    org_id     UUID        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    task_id    UUID        REFERENCES tasks (id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Поиск незакрытых занятий: запланированные за недавние даты.
CREATE INDEX idx_sessions_scheduled_date ON sessions (date) WHERE status = 'scheduled';
