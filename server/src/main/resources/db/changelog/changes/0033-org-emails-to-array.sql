--liquibase formatted sql

--changeset dev:0033-org-emails-to-array
ALTER TABLE org_emails
    ALTER COLUMN to_addresses TYPE TEXT[]
    USING string_to_array(to_addresses, ',');
