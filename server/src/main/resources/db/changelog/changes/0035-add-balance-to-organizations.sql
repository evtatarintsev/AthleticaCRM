--liquibase formatted sql

--changeset dev:0035-add-balance-to-organizations

ALTER TABLE organizations
    ADD COLUMN balance NUMERIC(12, 2) NOT NULL DEFAULT 0;

COMMENT ON COLUMN organizations.balance IS 'Current organisation balance';
