--liquibase formatted sql

--changeset dev:0025-client-gender
CREATE TYPE gender AS ENUM ('MALE', 'FEMALE');

ALTER TABLE clients ADD COLUMN gender gender NOT NULL DEFAULT 'MALE';

ALTER TABLE clients ALTER COLUMN gender DROP DEFAULT;
