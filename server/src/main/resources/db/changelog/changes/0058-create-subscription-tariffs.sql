--liquibase formatted sql

--changeset dev:0058-create-subscription-tariffs
CREATE TABLE subscription_tariffs
(
    id             UUID           NOT NULL DEFAULT uuidv7() PRIMARY KEY,
    org_id         UUID           NOT NULL REFERENCES organizations (id),
    name           VARCHAR(255)   NOT NULL,
    sessions_count INT,
    duration_value INT            NOT NULL,
    duration_unit  VARCHAR(16)    NOT NULL,
    price          NUMERIC(12, 2) NOT NULL,
    is_archived    BOOLEAN        NOT NULL DEFAULT false,
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now()
);
