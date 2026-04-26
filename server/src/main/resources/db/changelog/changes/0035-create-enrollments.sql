--liquibase formatted sql

--changeset dev:0035-create-enrollments
CREATE TABLE enrollments
(
    id          UUID        NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    group_id    UUID        NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
    client_id   UUID        NOT NULL REFERENCES clients (id) ON DELETE CASCADE,
    enrolled_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    left_at     TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_active_enrollment ON enrollments (client_id, group_id) WHERE left_at IS NULL;
CREATE INDEX idx_enrollments_group ON enrollments (group_id);
CREATE INDEX idx_enrollments_client ON enrollments (client_id);

INSERT INTO enrollments (group_id, client_id, enrolled_at)
SELECT group_id, client_id, now()
FROM client_groups;

DROP TABLE client_groups;
