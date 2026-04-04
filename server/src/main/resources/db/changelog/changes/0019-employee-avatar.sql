--liquibase formatted sql

--changeset dev:0019-employee-avatar
ALTER TABLE employees ADD COLUMN avatar_id UUID REFERENCES uploads(id) ON DELETE SET NULL;
