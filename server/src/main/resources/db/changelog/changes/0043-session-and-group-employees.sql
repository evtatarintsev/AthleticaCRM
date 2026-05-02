--liquibase formatted sql

--changeset dev:0043-session-and-group-employees

CREATE TABLE group_employees
(
    group_id    UUID NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
    employee_id UUID NOT NULL REFERENCES employees (id) ON DELETE CASCADE,
    PRIMARY KEY (group_id, employee_id)
);

CREATE INDEX idx_group_employees_employee ON group_employees (employee_id);

CREATE TABLE session_employees
(
    session_id  UUID NOT NULL REFERENCES sessions (id) ON DELETE CASCADE,
    employee_id UUID NOT NULL REFERENCES employees (id) ON DELETE CASCADE,
    PRIMARY KEY (session_id, employee_id)
);

CREATE INDEX idx_session_employees_employee ON session_employees (employee_id);

ALTER TABLE sessions
    ADD COLUMN is_employee_assignment_overridden BOOLEAN NOT NULL DEFAULT FALSE;
