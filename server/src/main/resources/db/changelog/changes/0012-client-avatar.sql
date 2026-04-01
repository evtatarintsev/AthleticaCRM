-- liquibase formatted sql

-- changeset athletica:0012-client-avatar
ALTER TABLE clients ADD COLUMN avatar_id UUID REFERENCES uploads(id) ON DELETE SET NULL;
