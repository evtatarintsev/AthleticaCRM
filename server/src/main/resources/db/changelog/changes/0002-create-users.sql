--liquibase formatted sql

--changeset dev:0002-create-users
CREATE TABLE users
(
    id            UUID         NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    login         VARCHAR(255) NOT NULL UNIQUE,
    name          VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
