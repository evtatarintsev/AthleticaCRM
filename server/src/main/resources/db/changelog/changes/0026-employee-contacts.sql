--liquibase formatted sql

--changeset dev:0026-employee-contacts
ALTER TABLE employees ADD COLUMN phone_no VARCHAR(50)  NULL;
ALTER TABLE employees ADD COLUMN email    VARCHAR(255) NULL;
