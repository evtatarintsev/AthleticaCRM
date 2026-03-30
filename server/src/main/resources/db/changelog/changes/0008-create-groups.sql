--liquibase formatted sql

--changeset dev:0008-create-groups
CREATE TABLE groups
(
    id         UUID         NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    org_id     UUID         NOT NULL REFERENCES organizations (id),
    name       VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
