--liquibase formatted sql

--changeset dev:0022-groups-unique-name
ALTER TABLE groups
    ADD CONSTRAINT uq_groups_org_name UNIQUE (org_id, name);
