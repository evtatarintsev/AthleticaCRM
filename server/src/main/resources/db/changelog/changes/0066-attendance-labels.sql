--liquibase formatted sql

--changeset dev:0066-attendance-labels
CREATE TYPE attendance_label_scope AS ENUM ('present', 'absent', 'any');

CREATE TABLE attendance_labels
(
    id          UUID                   NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    org_id      UUID                   NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    name        TEXT                   NOT NULL,
    scope       attendance_label_scope NOT NULL,
    position    INT                    NOT NULL DEFAULT 0,
    archived_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ            NOT NULL DEFAULT now(),
    CONSTRAINT check_attendance_label_name_not_blank CHECK (btrim(name) <> '')
);

CREATE UNIQUE INDEX uq_attendance_labels_org_name ON attendance_labels (org_id, lower(name));

--changeset dev:0066-attendance-labels-defaults
INSERT INTO attendance_labels (org_id, name, scope, position)
SELECT o.id, d.name, d.scope::attendance_label_scope, d.position
FROM organizations o
         CROSS JOIN (VALUES ('Опоздал', 'present', 1),
                            ('Ушёл раньше', 'present', 2),
                            ('Болеет', 'absent', 3),
                            ('Предупредил', 'absent', 4),
                            ('Прогул', 'absent', 5)) AS d(name, scope, position);
