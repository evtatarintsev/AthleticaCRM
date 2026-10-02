--liquibase formatted sql

--changeset dev:0068-attendance-label-links
CREATE TABLE session_attendance_labels
(
    attendance_id UUID NOT NULL REFERENCES session_attendance (id) ON DELETE CASCADE,
    label_id      UUID NOT NULL REFERENCES attendance_labels (id) ON DELETE RESTRICT,
    PRIMARY KEY (attendance_id, label_id)
);

CREATE INDEX idx_session_attendance_labels_label ON session_attendance_labels (label_id);
