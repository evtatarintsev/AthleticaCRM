--liquibase formatted sql

--changeset dev:0017-rename-sports-to-disciplines
ALTER TABLE sports RENAME TO disciplines;
ALTER TABLE disciplines RENAME CONSTRAINT sports_org_name_unique TO disciplines_org_name_unique;
