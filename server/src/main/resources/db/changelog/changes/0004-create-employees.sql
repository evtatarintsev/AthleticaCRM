--liquibase formatted sql

--changeset dev:0004-create-employees
CREATE TABLE employees
(
    id        UUID        NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    user_id   UUID        NOT NULL REFERENCES users (id) ON DELETE RESTRICT,
    org_id    UUID        NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    role_id   UUID        NOT NULL REFERENCES roles (id) ON DELETE RESTRICT,
    is_owner  BOOLEAN     NOT NULL DEFAULT false,
    is_active BOOLEAN     NOT NULL DEFAULT true,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE (user_id, org_id)
);

CREATE UNIQUE INDEX one_owner_per_org
    ON employees (org_id)
    WHERE is_owner = true AND is_active = true;

CREATE INDEX idx_employees_org_id ON employees (org_id);
CREATE INDEX idx_employees_user_id ON employees (user_id);

--changeset dev:0004-create-employee-permission-overrides
CREATE TABLE employee_permission_overrides
(
    id             UUID         NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    employee_id    UUID         NOT NULL REFERENCES employees (id) ON DELETE CASCADE,
    permission_key VARCHAR(100) NOT NULL,
    is_granted     BOOLEAN      NOT NULL,

    UNIQUE (employee_id, permission_key)
);
