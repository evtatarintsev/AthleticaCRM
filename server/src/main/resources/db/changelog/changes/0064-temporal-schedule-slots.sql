--liquibase formatted sql

--changeset dev:0064-btree-gist-extension
CREATE EXTENSION IF NOT EXISTS btree_gist;

--changeset dev:0064-isodow-of-function splitStatements:false
CREATE FUNCTION isodow_of(d day_of_week) RETURNS INT
    LANGUAGE sql
    IMMUTABLE
    STRICT
    PARALLEL SAFE
AS
$$
SELECT CASE d
           WHEN 'MONDAY' THEN 1
           WHEN 'TUESDAY' THEN 2
           WHEN 'WEDNESDAY' THEN 3
           WHEN 'THURSDAY' THEN 4
           WHEN 'FRIDAY' THEN 5
           WHEN 'SATURDAY' THEN 6
           WHEN 'SUNDAY' THEN 7
           END
$$;

--changeset dev:0064-schedule-slots-validity
ALTER TABLE schedule_slots
    ADD COLUMN validity DATERANGE;

UPDATE schedule_slots
SET validity = daterange(created_at::DATE, NULL);

ALTER TABLE schedule_slots
    ALTER COLUMN validity SET NOT NULL;

ALTER TABLE schedule_slots
    ADD CONSTRAINT check_validity_bounded_below CHECK (lower(validity) IS NOT NULL);

ALTER TABLE schedule_slots
    ADD CONSTRAINT check_validity_not_empty CHECK (NOT isempty(validity));

--changeset dev:0064-no-overlapping-slot-versions
ALTER TABLE schedule_slots
    ADD CONSTRAINT no_overlapping_slot_versions
        EXCLUDE USING gist (
            group_id WITH =,
            day_of_week WITH =,
            start_time WITH =,
            validity WITH &&
            );

CREATE INDEX idx_schedule_slots_group_validity ON schedule_slots USING gist (group_id, validity);
