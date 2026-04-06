--liquibase formatted sql

--changeset dev:0021-client-groups-unique
ALTER TABLE client_groups
    ADD CONSTRAINT uq_client_groups UNIQUE (client_id, group_id);
