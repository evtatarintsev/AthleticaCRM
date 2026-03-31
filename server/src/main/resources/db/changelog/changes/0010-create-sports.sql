--liquibase formatted sql

--changeset dev:0010-create-sports
CREATE TABLE sports
(
    id         UUID         NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    org_id     UUID         NOT NULL REFERENCES organizations (id),
    name       VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT sports_org_name_unique UNIQUE (org_id, name)
);
