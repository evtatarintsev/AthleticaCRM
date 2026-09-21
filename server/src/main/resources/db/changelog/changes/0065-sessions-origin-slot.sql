--liquibase formatted sql

--changeset dev:0065-sessions-origin-slot-id
ALTER TABLE sessions
    ADD COLUMN origin_slot_id UUID REFERENCES schedule_slots (id) ON DELETE RESTRICT;

UPDATE sessions s
SET origin_slot_id = ss.id
FROM schedule_slots ss
WHERE ss.group_id = s.group_id
  AND ss.day_of_week::TEXT = s.origin_day_of_week
  AND ss.start_time = s.origin_start_time;

CREATE INDEX idx_sessions_origin_slot ON sessions (origin_slot_id);

--changeset dev:0065-sessions-origin-unique
DROP INDEX uq_sessions_generated;

CREATE UNIQUE INDEX uq_sessions_origin ON sessions (origin_slot_id, origin_date);

ALTER TABLE sessions
    DROP COLUMN origin_day_of_week,
    DROP COLUMN origin_start_time,
    DROP COLUMN is_manual;

--changeset dev:0065-sessions-group-restrict
ALTER TABLE sessions
    DROP CONSTRAINT sessions_group_id_fkey;

ALTER TABLE sessions
    ADD CONSTRAINT sessions_group_id_fkey
        FOREIGN KEY (group_id) REFERENCES groups (id) ON DELETE RESTRICT;
