--liquibase formatted sql

--changeset dev:0061-client-state
CREATE TYPE client_state AS ENUM ('ACTIVE', 'ARCHIVED');

ALTER TABLE clients ADD COLUMN state client_state NOT NULL DEFAULT 'ACTIVE';

CREATE INDEX idx_clients_active ON clients (org_id) WHERE state = 'ACTIVE';
