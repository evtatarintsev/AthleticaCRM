--liquibase formatted sql

--changeset dev:0041-add-lead-source-to-clients
ALTER TABLE clients ADD COLUMN lead_source_id UUID REFERENCES lead_sources(id);
