--liquibase formatted sql

--changeset dev:0013-organizations-timezone
ALTER TABLE organizations ADD COLUMN timezone VARCHAR(64) NOT NULL DEFAULT 'UTC';
