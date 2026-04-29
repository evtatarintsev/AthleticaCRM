--liquibase formatted sql

--changeset dev:0039-employee-branches
ALTER TABLE employees
    ADD COLUMN all_branches_access BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE employee_branches
(
    employee_id UUID NOT NULL REFERENCES employees (id) ON DELETE CASCADE,
    branch_id   UUID NOT NULL REFERENCES branches (id) ON DELETE CASCADE,
    PRIMARY KEY (employee_id, branch_id)
);

ALTER TABLE groups
    ADD COLUMN branch_id UUID REFERENCES branches (id);

UPDATE groups g
SET branch_id = (SELECT id FROM branches WHERE org_id = g.org_id LIMIT 1);

ALTER TABLE groups
    ALTER COLUMN branch_id SET NOT NULL;
