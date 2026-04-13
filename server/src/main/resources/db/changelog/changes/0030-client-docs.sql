--liquibase formatted sql

--changeset evgeny:0030-client-docs
CREATE TABLE client_docs (
    id UUID PRIMARY KEY DEFAULT uuidv7(),
    client_id UUID NOT NULL REFERENCES clients(id) ON DELETE CASCADE,
    org_id UUID NOT NULL REFERENCES organizations(id),
    upload_id UUID NOT NULL REFERENCES uploads(id),
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ DEFAULT now()
);
CREATE INDEX idx_client_docs_client_id ON client_docs(client_id);
