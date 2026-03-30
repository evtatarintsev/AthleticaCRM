--liquibase formatted sql

--changeset dev:0009-create-client-groups
CREATE TABLE client_groups
(
    client_id UUID NOT NULL REFERENCES clients (id) ON DELETE CASCADE,
    group_id  UUID NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
    PRIMARY KEY (client_id, group_id)
);
