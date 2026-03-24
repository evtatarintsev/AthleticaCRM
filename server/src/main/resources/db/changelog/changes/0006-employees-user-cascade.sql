--liquibase formatted sql

--changeset dev:0006-employees-user-cascade
ALTER TABLE employees
    DROP CONSTRAINT employees_user_id_fkey,
    ADD CONSTRAINT employees_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;
