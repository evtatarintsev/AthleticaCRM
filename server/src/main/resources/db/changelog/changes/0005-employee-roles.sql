--liquibase formatted sql

--changeset dev:0005-drop-employee-role-id
ALTER TABLE employees DROP COLUMN role_id;

--changeset dev:0005-create-employee-roles
CREATE TABLE employee_roles
(
    employee_id UUID NOT NULL REFERENCES employees (id) ON DELETE CASCADE,
    role_id     UUID NOT NULL REFERENCES roles (id) ON DELETE RESTRICT,
    PRIMARY KEY (employee_id, role_id)
);

CREATE INDEX idx_employee_roles_employee_id ON employee_roles (employee_id);
CREATE INDEX idx_employee_roles_role_id ON employee_roles (role_id);
