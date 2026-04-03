--liquibase formatted sql

--changeset dev:0015-create-audit-logs
CREATE TABLE audit_logs
(
    id          UUID         NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    org_id      UUID         NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    user_id     UUID         REFERENCES users (id) ON DELETE SET NULL,
    username    VARCHAR(255) NOT NULL,
    action_type VARCHAR(50)  NOT NULL,
    entity_type VARCHAR(100),
    entity_id   UUID,
    data        JSONB,
    ip_address  VARCHAR(45),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_org_created ON audit_logs (org_id, created_at DESC);
CREATE INDEX idx_audit_user_id ON audit_logs (user_id);
CREATE INDEX idx_audit_action_type ON audit_logs (action_type);
CREATE INDEX idx_audit_entity ON audit_logs (entity_type, entity_id);
