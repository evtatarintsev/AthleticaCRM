--liquibase formatted sql

--changeset dev:0001-create-organizations
CREATE TABLE organizations
(
    id   UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    name VARCHAR(255) NOT NULL
);
