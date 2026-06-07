--liquibase formatted sql

--changeset dev:0059-create-memberships
CREATE TABLE memberships
(
    id                 UUID           NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    org_id             UUID           NOT NULL REFERENCES organizations (id),
    client_id          UUID           NOT NULL REFERENCES clients (id) ON DELETE CASCADE,
    tariff_plan_id     UUID           REFERENCES subscription_tariffs (id),
    name               VARCHAR(255)   NOT NULL,
    sessions_total     INT,
    sessions_remaining INT,
    start_date         DATE           NOT NULL,
    end_date           DATE           NOT NULL,
    price              NUMERIC(12, 2) NOT NULL,
    issued_by          UUID           REFERENCES employees (id) ON DELETE SET NULL,
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX idx_memberships_client ON memberships (client_id, created_at DESC);
CREATE INDEX idx_memberships_org ON memberships (org_id);
