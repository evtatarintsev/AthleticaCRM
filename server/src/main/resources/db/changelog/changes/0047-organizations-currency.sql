--liquibase formatted sql

--changeset dev:0047-organizations-currency

ALTER TABLE organizations
    ADD COLUMN currency CHAR(3) NOT NULL DEFAULT 'RUB';

ALTER TABLE organizations
    ADD CONSTRAINT chk_org_currency
        CHECK (currency IN ('RUB', 'USD', 'EUR', 'KZT', 'BYN', 'UAH'));
