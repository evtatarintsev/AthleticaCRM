--liquibase formatted sql

--changeset dev:0040-create-halls
CREATE TABLE halls
(
    id         UUID         NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    org_id     UUID         NOT NULL REFERENCES organizations (id),
    branch_id  UUID         NOT NULL REFERENCES branches (id),
    name       VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT halls_org_branch_name_unique UNIQUE (org_id, branch_id, name)
);

CREATE INDEX idx_halls_org_branch_name ON halls (org_id, branch_id, name);
