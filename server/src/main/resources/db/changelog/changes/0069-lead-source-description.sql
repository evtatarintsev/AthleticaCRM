--liquibase formatted sql

--changeset dev:0069-lead-source-description
ALTER TABLE lead_sources ADD COLUMN description TEXT NOT NULL DEFAULT '';
