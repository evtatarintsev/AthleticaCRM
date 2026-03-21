--liquibase formatted sql

--changeset dev:0001-create-organizations
CREATE TABLE organizations
(
    id         UUID         NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
