--liquibase formatted sql

--changeset dev:0023-group-disciplines
CREATE TABLE group_disciplines (
    group_id      UUID NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
    discipline_id UUID NOT NULL REFERENCES disciplines (id) ON DELETE CASCADE,
    PRIMARY KEY (group_id, discipline_id)
);
