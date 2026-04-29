--liquibase formatted sql

--changeset dev:0038-create-branches
CREATE TABLE branches
(
    id         UUID         NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    org_id     UUID         NOT NULL REFERENCES organizations (id),
    name       VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT branches_org_name_unique UNIQUE (org_id, name)
);