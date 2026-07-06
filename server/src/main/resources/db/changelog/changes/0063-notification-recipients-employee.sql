--liquibase formatted sql

--changeset dev:0063-notification-recipients-employee
DROP TABLE notification_recipients;

CREATE TABLE notification_recipients (
    notification_id UUID        NOT NULL REFERENCES notifications (id) ON DELETE CASCADE,
    employee_id     UUID        NOT NULL REFERENCES employees (id) ON DELETE CASCADE,
    is_read         BOOLEAN     NOT NULL DEFAULT false,
    read_at         TIMESTAMPTZ,
    PRIMARY KEY (notification_id, employee_id)
);

CREATE INDEX idx_notification_recipients_employee_id ON notification_recipients (employee_id);
