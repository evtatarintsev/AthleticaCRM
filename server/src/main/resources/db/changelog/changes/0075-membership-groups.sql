--liquibase formatted sql

--changeset dev:0075-membership-groups
-- Группы, в которых действует абонемент; задаются при выдаче, хотя бы одна.
-- Существующие абонементы не заполняются: данных до продакшена нет.
CREATE TABLE membership_groups
(
    membership_id UUID NOT NULL REFERENCES memberships (id) ON DELETE CASCADE,
    group_id      UUID NOT NULL REFERENCES groups (id) ON DELETE CASCADE,
    PRIMARY KEY (membership_id, group_id)
);

CREATE INDEX idx_membership_groups_group ON membership_groups (group_id);
