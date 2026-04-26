--liquibase formatted sql

--changeset dev:0036-sessions-origin-tracking

ALTER TABLE sessions ADD COLUMN origin_day_of_week TEXT;
ALTER TABLE sessions ADD COLUMN origin_start_time   TIME;
ALTER TABLE sessions ADD COLUMN origin_date         DATE;
ALTER TABLE sessions ADD COLUMN is_rescheduled      BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE sessions ADD COLUMN cancelled_at        TIMESTAMPTZ;
ALTER TABLE sessions ADD COLUMN completed_at        TIMESTAMPTZ;

CREATE UNIQUE INDEX uq_sessions_generated
    ON sessions (group_id, origin_day_of_week, origin_start_time, origin_date)
    WHERE origin_day_of_week IS NOT NULL;
