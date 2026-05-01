--liquibase formatted sql

--changeset dev:0041-halls-in-schedule-and-sessions
INSERT INTO halls (id, org_id, branch_id, name)
SELECT uuidv7(), b.org_id, b.id, 'Основной зал'
FROM branches b
ON CONFLICT (org_id, branch_id, name) DO NOTHING;

ALTER TABLE schedule_slots
    ADD COLUMN hall_id UUID REFERENCES halls (id);

UPDATE schedule_slots ss
SET hall_id = h.id
FROM groups g
         JOIN halls h ON h.org_id = g.org_id AND h.branch_id = g.branch_id AND h.name = 'Основной зал'
WHERE ss.group_id = g.id
  AND ss.org_id = g.org_id;

ALTER TABLE schedule_slots
    ALTER COLUMN hall_id SET NOT NULL;

ALTER TABLE sessions
    ADD COLUMN hall_id UUID REFERENCES halls (id);

UPDATE sessions s
SET hall_id = COALESCE(
        (
            SELECT ss.hall_id
            FROM schedule_slots ss
            WHERE ss.group_id = s.group_id
              AND ss.day_of_week::TEXT = s.origin_day_of_week
              AND ss.start_time = s.origin_start_time
            ORDER BY ss.id
            LIMIT 1
        ),
        (
            SELECT h.id
            FROM groups g
                     JOIN halls h ON h.org_id = g.org_id AND h.branch_id = g.branch_id AND h.name = 'Основной зал'
            WHERE g.id = s.group_id
            LIMIT 1
        )
                )
WHERE s.group_id IS NOT NULL;

UPDATE sessions s
SET hall_id = sub.hall_id
FROM (
         SELECT s2.id AS session_id,
                (
                    SELECT h.id
                    FROM halls h
                    WHERE h.org_id = s2.org_id
                    ORDER BY h.created_at
                    LIMIT 1
                )   AS hall_id
         FROM sessions s2
         WHERE s2.hall_id IS NULL
     ) sub
WHERE s.id = sub.session_id;

ALTER TABLE sessions
    ALTER COLUMN hall_id SET NOT NULL;
