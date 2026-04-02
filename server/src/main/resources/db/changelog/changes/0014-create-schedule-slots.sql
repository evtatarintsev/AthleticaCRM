--liquibase formatted sql

--changeset dev:0014-create-schedule-slots

CREATE TYPE day_of_week AS ENUM (
    'MONDAY',
    'TUESDAY',
    'WEDNESDAY',
    'THURSDAY',
    'FRIDAY',
    'SATURDAY',
    'SUNDAY'
);

CREATE TABLE schedule_slots (
    id          UUID        NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    org_id      UUID        NOT NULL REFERENCES organizations (id),
    group_id    UUID        NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
    day_of_week day_of_week NOT NULL,
    start_time  TIME        NOT NULL,
    end_time    TIME        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT check_times CHECK (end_time > start_time)
);

CREATE INDEX idx_schedule_slots_group ON schedule_slots (group_id);
