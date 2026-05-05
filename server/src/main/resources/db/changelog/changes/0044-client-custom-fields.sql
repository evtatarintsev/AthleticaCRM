--liquibase formatted sql

--changeset dev:0044-client-custom-fields
ALTER TABLE clients ADD COLUMN custom_fields JSONB NOT NULL DEFAULT '[]';
