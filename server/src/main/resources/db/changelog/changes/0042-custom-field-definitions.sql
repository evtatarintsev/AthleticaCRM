--liquibase formatted sql

--changeset dev:0042-custom-field-definitions
CREATE TYPE custom_field_type AS ENUM (
    'text', 'number', 'date', 'select', 'boolean', 'phone', 'email', 'url'
);

CREATE TABLE custom_field_definitions
(
    id            UUID         NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    org_id        UUID         NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    entity_type   VARCHAR(50)  NOT NULL,
    field_key     VARCHAR(100) NOT NULL,
    label         VARCHAR(255) NOT NULL,
    field_type    custom_field_type NOT NULL,
    config        JSONB        NOT NULL DEFAULT '{}',
    is_required   BOOLEAN      NOT NULL DEFAULT FALSE,
    is_searchable BOOLEAN      NOT NULL DEFAULT FALSE,
    is_sortable   BOOLEAN      NOT NULL DEFAULT FALSE,
    display_order INT          NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT custom_field_definitions_org_entity_key_unique UNIQUE (org_id, entity_type, field_key)
);

CREATE INDEX idx_custom_field_definitions_org_entity
    ON custom_field_definitions (org_id, entity_type);
