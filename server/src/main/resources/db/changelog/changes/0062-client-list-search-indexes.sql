--liquibase formatted sql

--changeset dev:0062-client-list-search-indexes
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_clients_name_trgm ON clients USING gin (name gin_trgm_ops);

CREATE INDEX idx_balance_journal_latest ON client_balance_journal (org_id, client_id, created_at DESC);
