--liquibase formatted sql

--changeset dev:0003-create-roles
CREATE TABLE roles
(
    id          UUID         NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    org_id      UUID         NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    name        VARCHAR(255) NOT NULL,
    template_id VARCHAR(50)  NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_roles_org_id ON roles (org_id);

--changeset dev:0003-create-role-permissions
CREATE TABLE role_permissions
(
    role_id        UUID         NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    permission_key VARCHAR(100) NOT NULL,
    PRIMARY KEY (role_id, permission_key)
);
